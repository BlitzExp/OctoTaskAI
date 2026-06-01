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

    public BotOrchestrator(TelegramClient telegram,
                           List<BotTool> tools,
                           LoginService loginService,
                           SemanticRouter router,
                           ToolArgumentResolver resolver,
                           ReplyComposer composer) {
        this.telegram = telegram;
        this.toolsByName = tools.stream().collect(Collectors.toMap(BotTool::getName, Function.identity()));
        this.loginService = loginService;
        this.router = router;
        this.resolver = resolver;
        this.composer = composer;
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
            telegram.sendMessage(chatId, loginService.handleLogout(chatId));
            return;
        }

        // --- Login gate ---
        BotUserLink identity = loginService.current(chatId);
        if (identity == null) {
            telegram.sendMessage(chatId,
                    "Primero inicia sesión para que pueda identificarte:\n" +
                    "/login <código de acceso> <tu nombre>");
            return;
        }

        // --- Semantic routing ---
        var decision = router.route(text);
        if (decision.isEmpty()) {
            telegram.sendMessage(chatId,
                    "No entendí bien tu solicitud, " + identity.getAppUserName() + ". " +
                    "Puedes pedirme cosas como: \"mis tareas pendientes\", \"mis kpis\", " +
                    "\"tareas del equipo\", \"crear una tarea\" o \"completar tarea\".");
            return;
        }

        String toolName = decision.get().getToolName();
        BotTool tool = toolsByName.get(toolName);
        if (tool == null) {
            log.error("Router chose unknown tool '{}'", toolName);
            telegram.sendMessage(chatId, "Encontré una acción (" + toolName + ") pero no está disponible.");
            return;
        }

        // --- Argument resolution ---
        ToolArgumentResolver.Resolution res = resolver.resolve(tool, identity, text);
        if (!res.missingRequired.isEmpty()) {
            telegram.sendMessage(chatId,
                    "Para esto necesito que me indiques: " + String.join(", ", res.missingRequired) +
                    ". Por favor inclúyelo en tu mensaje.");
            return;
        }

        // --- Execute + reply ---
        try {
            Object rawData = tool.execute(res.args);
            String reply = composer.compose(text, rawData);
            telegram.sendMessage(chatId, reply);
        } catch (Exception e) {
            log.error("Tool execution failed tool={} args={}", toolName, res.args, e);
            telegram.sendMessage(chatId, "Ocurrió un error al consultar la base de datos: " + e.getMessage());
        }
    }

    private String welcome(Long chatId) {
        boolean loggedIn = loginService.isLoggedIn(chatId);
        StringBuilder sb = new StringBuilder("👋 Soy el asistente de OctoTask.\n\n");
        if (!loggedIn) {
            sb.append("Para empezar, vincula tu cuenta:\n/login <código de acceso> <tu nombre>\n\n");
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
