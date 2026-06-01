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
import com.octotask.bot.orchestrator.ReplyComposer;
import com.octotask.bot.orchestrator.ToolArgumentResolver;
import com.octotask.bot.router.SemanticRouter;
import com.octotask.bot.telegram.TelegramClient;
import com.octotask.bot.tools.BotTool;

/**
 * Behavioural tests for the router-based orchestrator: login gate, semantic
 * routing to a tool, and reply composition. No cloud LLM involved.
 */
@ExtendWith(MockitoExtension.class)
public class OctoTaskBotTest {

    @Mock private TelegramClient telegram;
    @Mock private LoginService loginService;
    @Mock private SemanticRouter router;
    @Mock private ToolArgumentResolver resolver;
    @Mock private ReplyComposer composer;
    @Mock private BotTool getPendingTasksTool;

    private final ObjectMapper mapper = new ObjectMapper();
    private BotOrchestrator orchestrator;

    private static final Long CHAT = 12345L;

    @BeforeEach
    void setUp() {
        lenient().when(getPendingTasksTool.getName()).thenReturn("get_pending_tasks");
        List<BotTool> tools = List.of(getPendingTasksTool);
        orchestrator = new BotOrchestrator(telegram, tools, loginService, router, resolver, composer);
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
        when(resolver.resolve(eq(getPendingTasksTool), eq(identity), anyString())).thenReturn(resolution);
        when(getPendingTasksTool.execute(any(JsonNode.class))).thenReturn(List.of());
        when(composer.compose(anyString(), any())).thenReturn("No tienes tareas pendientes 🎉");

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
        when(resolver.resolve(eq(getPendingTasksTool), eq(identity), anyString())).thenReturn(resolution);

        orchestrator.processIncomingMessage(CHAT, "tareas tarde");

        verify(getPendingTasksTool, never()).execute(any());
        verify(telegram).sendMessage(eq(CHAT), contains("sprintId"));
    }

    @Test
    public void lowConfidenceRouteFallsBackToHelp() {
        BotUserLink identity = new BotUserLink(CHAT, 7, "Diego", 3);
        when(loginService.current(CHAT)).thenReturn(identity);
        when(router.route(anyString())).thenReturn(Optional.empty());

        orchestrator.processIncomingMessage(CHAT, "blah blah");

        verify(telegram).sendMessage(eq(CHAT), contains("No entendí"));
    }
}
