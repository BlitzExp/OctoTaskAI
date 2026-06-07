package com.octotask.bot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.octotask.bot.data.BotUserLink;
import com.octotask.bot.login.LoginService;
import com.octotask.bot.orchestrator.BotOrchestrator;
import com.octotask.bot.orchestrator.ConversationMemory;
import com.octotask.bot.orchestrator.PendingActionStore;
import com.octotask.bot.orchestrator.ReplyComposer;
import com.octotask.bot.orchestrator.ToolArgumentResolver;
import com.octotask.bot.router.SemanticRouter;
import com.octotask.bot.telegram.TelegramClient;
import com.octotask.bot.tools.BotTool;

/**
 * Behavioural tests for the router-based orchestrator: login gate, semantic
 * routing to a tool, reply composition, and multi-turn slot filling. No cloud
 * LLM involved.
 */
@ExtendWith(MockitoExtension.class)
public class OctoTaskBotTest {

    @Mock private TelegramClient telegram;
    @Mock private LoginService loginService;
    @Mock private SemanticRouter router;
    @Mock private ToolArgumentResolver resolver;
    @Mock private ReplyComposer composer;
    @Mock private ConversationMemory memory;
    @Mock private BotTool getPendingTasksTool;

    // Real store so multi-turn state actually persists across messages.
    private final PendingActionStore pending = new PendingActionStore();
    private final ObjectMapper mapper = new ObjectMapper();
    private BotOrchestrator orchestrator;

    private static final Long CHAT = 12345L;

    @BeforeEach
    void setUp() {
        lenient().when(getPendingTasksTool.getName()).thenReturn("get_pending_tasks");
        List<BotTool> tools = List.of(getPendingTasksTool);
        orchestrator = new BotOrchestrator(telegram, tools, loginService, router, resolver, composer, memory, pending);
    }

    @Test
    public void unlinkedUserIsAskedToLogin() {
        when(loginService.current(CHAT)).thenReturn(null);

        orchestrator.processIncomingMessage(CHAT, "mis tareas pendientes");

        verify(telegram).sendMessage(eq(CHAT), contains("/login"));
        verifyNoInteractions(router);
    }

    @Test
    public void loggedInUserGetsRoutedAndAnswered() throws Exception {
        BotUserLink identity = new BotUserLink(CHAT, 7, "Diego", 3);
        when(loginService.current(CHAT)).thenReturn(identity);
        when(router.route(anyString()))
                .thenReturn(Optional.of(new SemanticRouter.Decision("get_pending_tasks", 0.9, "mis pendientes")));

        ToolArgumentResolver.Resolution resolution =
                new ToolArgumentResolver.Resolution(mapper.createObjectNode().put("userName", "Diego"), List.of());
        when(resolver.resolve(eq(getPendingTasksTool), eq(identity), anyString(), any())).thenReturn(resolution);
        when(getPendingTasksTool.execute(any(JsonNode.class))).thenReturn(List.of());
        when(composer.compose(anyString(), any(), any())).thenReturn("No tienes tareas pendientes 🎉");

        orchestrator.processIncomingMessage(CHAT, "¿qué tengo pendiente?");

        verify(getPendingTasksTool).execute(any(JsonNode.class));
        verify(telegram).sendMessage(eq(CHAT), eq("No tienes tareas pendientes 🎉"));
    }

    @Test
    public void missingRequiredArgsAsksUserInsteadOfExecuting() throws Exception {
        BotUserLink identity = new BotUserLink(CHAT, 7, "Diego", 3);
        when(loginService.current(CHAT)).thenReturn(identity);
        when(router.route(anyString()))
                .thenReturn(Optional.of(new SemanticRouter.Decision("get_pending_tasks", 0.9, "x")));

        ToolArgumentResolver.Resolution resolution =
                new ToolArgumentResolver.Resolution(mapper.createObjectNode(), List.of("sprintId"));
        when(resolver.resolve(eq(getPendingTasksTool), eq(identity), anyString(), any())).thenReturn(resolution);

        orchestrator.processIncomingMessage(CHAT, "tareas tarde");

        verify(getPendingTasksTool, never()).execute(any());
        verify(telegram).sendMessage(eq(CHAT), contains("sprint"));
    }

    @Test
    public void lowConfidenceRouteFallsBackToHelp() {
        BotUserLink identity = new BotUserLink(CHAT, 7, "Diego", 3);
        when(loginService.current(CHAT)).thenReturn(identity);
        when(router.route(anyString())).thenReturn(Optional.empty());

        orchestrator.processIncomingMessage(CHAT, "blah blah");

        verify(telegram).sendMessage(eq(CHAT), contains("No entendí"));
    }

    @Test
    public void multiTurnSlotFillingCompletesAcrossMessages() throws Exception {
        BotUserLink identity = new BotUserLink(CHAT, 7, "Diego", 3);
        when(loginService.current(CHAT)).thenReturn(identity);

        // Turn 1: routes to the tool but a required field is missing -> ask + remember.
        when(router.route(anyString()))
                .thenReturn(Optional.of(new SemanticRouter.Decision("get_pending_tasks", 0.9, "create")));
        ToolArgumentResolver.Resolution incomplete =
                new ToolArgumentResolver.Resolution(mapper.createObjectNode().put("name", "Test"), List.of("sprintId"));
        when(resolver.resolve(eq(getPendingTasksTool), eq(identity), anyString(), any())).thenReturn(incomplete);

        orchestrator.processIncomingMessage(CHAT, "quiero crear una tarea");

        verify(getPendingTasksTool, never()).execute(any());
        // The action is now pending for this chat.
        org.junit.jupiter.api.Assertions.assertTrue(pending.has(CHAT));

        // Turn 2: the follow-up supplies the missing field. It must NOT go back to the router;
        // it is resolved against the pending tool (5-arg overload with seed args) and executed.
        ToolArgumentResolver.Resolution complete =
                new ToolArgumentResolver.Resolution(
                        mapper.createObjectNode().put("name", "Test").put("sprintId", 5), List.of());
        when(resolver.resolve(eq(getPendingTasksTool), eq(identity), anyString(), any(), any())).thenReturn(complete);
        when(getPendingTasksTool.execute(any(JsonNode.class))).thenReturn(List.of());
        when(composer.compose(anyString(), any(), any())).thenReturn("Tarea creada ✅");

        orchestrator.processIncomingMessage(CHAT, "sprint 5");

        verify(getPendingTasksTool).execute(any(JsonNode.class));
        verify(telegram).sendMessage(eq(CHAT), eq("Tarea creada ✅"));
        // Router is only consulted on turn 1, never for the follow-up.
        verify(router, times(1)).route(anyString());
        org.junit.jupiter.api.Assertions.assertFalse(pending.has(CHAT));
    }

    @Test
    public void cancelClearsPendingAction() {
        BotUserLink identity = new BotUserLink(CHAT, 7, "Diego", 3);
        when(loginService.current(CHAT)).thenReturn(identity);
        when(router.route(anyString()))
                .thenReturn(Optional.of(new SemanticRouter.Decision("get_pending_tasks", 0.9, "create")));
        ToolArgumentResolver.Resolution incomplete =
                new ToolArgumentResolver.Resolution(mapper.createObjectNode(), List.of("sprintId"));
        when(resolver.resolve(eq(getPendingTasksTool), eq(identity), anyString(), any())).thenReturn(incomplete);

        orchestrator.processIncomingMessage(CHAT, "crear una tarea");
        org.junit.jupiter.api.Assertions.assertTrue(pending.has(CHAT));

        orchestrator.processIncomingMessage(CHAT, "/cancel");

        org.junit.jupiter.api.Assertions.assertFalse(pending.has(CHAT));
        verify(telegram).sendMessage(eq(CHAT), contains("cancelé la acción en curso"));
    }
}
