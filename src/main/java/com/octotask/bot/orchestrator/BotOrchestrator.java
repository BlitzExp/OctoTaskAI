package com.octotask.bot.orchestrator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.octotask.bot.data.BotUserLink;
import com.octotask.bot.login.LoginService;
import com.octotask.bot.router.SemanticRouter;
import com.octotask.bot.telegram.TelegramClient;
import com.octotask.bot.tools.BotTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routes an incoming Telegram message to the right action:
 *
 *   1. login gate — unlinked chats are asked to /login;
 *   2. semantic router — local embeddings pick which tool the message maps to;
 *   3. argument resolver — identity + local LLM fill the tool's parameters;
 *   4. tool execution — against the OctoTask Oracle DB;
 *   5. reply composer — local LLM phrases the result for Telegram.
 *
 * No cloud LLM is involved; everything runs locally except the DB.
 */
@Service
public class BotOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(BotOrchestrator.class);

    private final TelegramClient telegram;
    private final Map<String, BotTool> toolsByName;
    private final LoginService loginService;
    private final SemanticRouter router;
    private final ToolArgumentResolver resolver;
    private final ReplyComposer composer;
    private final ConversationMemory memory;
    private final PendingActionStore pending;

    public BotOrchestrator(TelegramClient telegram,
                           List<BotTool> tools,
                           LoginService loginService,
                           SemanticRouter router,
                           ToolArgumentResolver resolver,
                           ReplyComposer composer,
                           ConversationMemory memory,
                           PendingActionStore pending) {
        this.telegram = telegram;
        this.toolsByName = tools.stream().collect(Collectors.toMap(BotTool::getName, Function.identity()));
        this.loginService = loginService;
        this.router = router;
        this.resolver = resolver;
        this.composer = composer;
        this.memory = memory;
        this.pending = pending;
    }

    public void processIncomingMessage(Long chatId, String userText) {
        log.info("Incoming chatId={} text={}", chatId, userText);
        String text = userText == null ? "" : userText.trim();

        // --- Commands ---
        if (text.equalsIgnoreCase("/start") || text.equalsIgnoreCase("/help")) {
            telegram.sendMessage(chatId, welcome(chatId));
            return;
        }
        if (text.toLowerCase().startsWith("/login")) {
            String rest = text.length() > 6 ? text.substring(6) : "";
            telegram.sendMessage(chatId, loginService.handleLogin(chatId, rest));
            return;
        }
        if (text.equalsIgnoreCase("/logout")) {
            memory.clear(chatId);
            telegram.sendMessage(chatId, loginService.handleLogout(chatId));
            return;
        }

        // Remember the user's turn (commands above are control, not conversation,
        // and /login carries the access code so we deliberately never store it).
        memory.recordUser(chatId, text);

        // --- Login gate ---
        BotUserLink identity = loginService.current(chatId);
        if (identity == null) {
            respond(chatId,
                    "Primero inicia sesión para que pueda identificarte:\n" +
                    "/login <tu código>");
            return;
        }

        // Conversation context fed to the local LLM so follow-ups resolve.
        String context = memory.recall(chatId);

        // --- Cancel a half-finished action ---
        if (text.equalsIgnoreCase("/cancel") || text.equalsIgnoreCase("cancelar")) {
            if (pending.has(chatId)) {
                pending.clear(chatId);
                respond(chatId, "Listo, cancelé la acción en curso. ¿En qué más te ayudo?");
            } else {
                respond(chatId, "No hay ninguna acción en curso.");
            }
            return;
        }

        // --- Continue a half-finished action (multi-turn slot filling) ---
        // While an action is pending we treat this message as supplying the
        // still-missing fields, instead of re-routing it through the router.
        PendingActionStore.PendingAction p = pending.get(chatId);
        if (p != null) {
            BotTool pendingTool = toolsByName.get(p.toolName);
            if (pendingTool == null) {
                pending.clear(chatId);                 // tool no longer exists; fall through to routing
            } else {
                p.attempts++;
                ToolArgumentResolver.Resolution res =
                        resolver.resolve(pendingTool, identity, text, context, p.args);
                if (res.missingRequired.isEmpty()) {
                    pending.clear(chatId);
                    executeAndReply(chatId, pendingTool, res.args, text, context);
                } else if (p.attempts >= PendingActionStore.MAX_ATTEMPTS) {
                    pending.clear(chatId);
                    respond(chatId, "Cancelé la acción porque no pude reunir los datos necesarios (" +
                            friendlyList(res.missingRequired) + "). Inténtalo de nuevo cuando quieras.");
                } else {
                    p.args.setAll(res.args);           // keep what we've gathered so far
                    p.missing = res.missingRequired;
                    respond(chatId, askFor(res.missingRequired));
                }
                return;
            }
        }

        // --- Semantic routing ---
        var decision = router.route(text);
        if (decision.isEmpty()) {
            respond(chatId,
                    "No entendí bien tu solicitud, " + identity.getAppUserName() + ". " +
                    "Puedes pedirme cosas como: \"mis tareas pendientes\", \"mis kpis\", " +
                    "\"tareas del equipo\", \"crear una tarea\" o \"completar tarea\".");
            return;
        }

        String toolName = decision.get().getToolName();
        BotTool tool = toolsByName.get(toolName);
        if (tool == null) {
            log.error("Router chose unknown tool '{}'", toolName);
            respond(chatId, "Encontré una acción (" + toolName + ") pero no está disponible.");
            return;
        }

        // --- Argument resolution (uses conversation context for follow-ups) ---
        ToolArgumentResolver.Resolution res = resolver.resolve(tool, identity, text, context);
        if (!res.missingRequired.isEmpty()) {
            // Remember the half-finished action so the user's next message completes it.
            pending.put(chatId, new PendingActionStore.PendingAction(toolName, res.args, res.missingRequired));
            respond(chatId, askFor(res.missingRequired));
            return;
        }

        // --- Execute + reply ---
        executeAndReply(chatId, tool, res.args, text, context);
    }

    /** Run a tool with resolved args and send the composed reply. */
    private void executeAndReply(Long chatId, BotTool tool, ObjectNode args, String text, String context) {
        try {
            Object rawData = tool.execute(args);
            // Write actions provide their own clear confirmation; only ask the
            // LLM to phrase results for read tools (where natural language helps).
            String confirmation = tool.successMessage(rawData);
            String reply = confirmation != null ? confirmation : composer.compose(text, rawData, context);
            respond(chatId, reply);
        } catch (Exception e) {
            log.error("Tool execution failed tool={} args={}", tool.getName(), args, e);
            respond(chatId, "Ocurrió un error al consultar la base de datos: " + e.getMessage());
        }
    }

    /** Ask the user for the fields still required to finish the current action. */
    private String askFor(List<String> missing) {
        return "Para completar la acción necesito que me indiques: " + friendlyList(missing) +
                ".\nPuedes dármelos en tu siguiente mensaje (uno o varios a la vez), " +
                "o escribe /cancel para cancelar.";
    }

    /** Map raw schema field names to friendly Spanish labels for prompts. */
    private String friendlyList(List<String> fields) {
        return fields.stream().map(BotOrchestrator::friendlyField).collect(Collectors.joining(", "));
    }

    private static String friendlyField(String field) {
        return switch (field) {
            case "name" -> "el nombre de la tarea";
            case "description" -> "una descripción";
            case "assigneeId" -> "a quién se asigna (ID de usuario)";
            case "sprintId" -> "el sprint (ID numérico)";
            case "priority" -> "la prioridad (1, 2 o 3)";
            case "estimatedHours" -> "horas estimadas";
            case "taskId" -> "el ID de la tarea";
            default -> field;
        };
    }

    /** Send a reply to the user and remember it as part of the conversation. */
    private void respond(Long chatId, String text) {
        memory.recordBot(chatId, text);
        telegram.sendMessage(chatId, text);
    }

    private String welcome(Long chatId) {
        boolean loggedIn = loginService.isLoggedIn(chatId);
        StringBuilder sb = new StringBuilder("👋 Soy el asistente de OctoTask.\n\n");
        if (!loggedIn) {
            sb.append("Para empezar, vincula tu cuenta con tu código personal:\n/login <tu código>\n\n");
        }
        sb.append("Luego puedes escribirme en lenguaje natural, por ejemplo:\n")
          .append("• \"¿qué tengo pendiente?\"\n")
          .append("• \"mis kpis\"\n")
          .append("• \"tareas del equipo\"\n")
          .append("• \"crear una tarea ...\"\n")
          .append("• \"completar la tarea 42\"\n\n")
          .append("Comandos: /login, /logout, /help");
        return sb.toString();
    }
}
