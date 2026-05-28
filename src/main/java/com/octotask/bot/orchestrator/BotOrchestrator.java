package com.octotask.bot.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.octotask.bot.ai.GeminiClient;
import com.octotask.bot.telegram.TelegramClient;
import com.octotask.bot.tools.BotTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BotOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(BotOrchestrator.class);

    private final GeminiClient gemini;
    private final TelegramClient telegram;
    private final ObjectMapper mapper;
    private final Map<String, BotTool> toolsByName;
    private final com.octotask.bot.ai.EmbeddingService embeddingService;
    private final com.octotask.bot.data.SemanticRoutingRepository semanticRepo;

    @Value("${bot.memory.max-chars:200}")
    private int memoryMaxChars;

    /** Per-chat ephemeral context window. Reset on restart. */
    private final Map<Long, String> chatMemory = new ConcurrentHashMap<>();
    private final Map<Long, ChatState> chatState = new ConcurrentHashMap<>();

    private enum ChatState {
        NONE,
        AWAIT_CREATE,
        AWAIT_SEARCH
    }

    public BotOrchestrator(GeminiClient gemini,
            TelegramClient telegram,
            List<BotTool> tools,
            ObjectMapper mapper,
            com.octotask.bot.ai.EmbeddingService embeddingService,
            Optional<com.octotask.bot.data.SemanticRoutingRepository> semanticRepo) {
        this.gemini = gemini;
        this.telegram = telegram;
        this.mapper = mapper;
        this.toolsByName = tools.stream().collect(Collectors.toMap(BotTool::getName, Function.identity()));
        this.embeddingService = embeddingService;
        this.semanticRepo = semanticRepo.orElse(null);
    }

    public void processIncomingMessage(Long chatId, String userText) {
        log.info("Incoming chatId={} text={}", chatId, userText);

        String pastConversation = chatMemory.getOrDefault(chatId, "");
        String memoryAwarePrompt = "Previous Conversation Context:\n" + pastConversation +
                "\n\nThe user's newest message is: '" + userText + "'";

        // If this is a new chat or user invoked /start, present the simple menu
        ChatState state = chatState.getOrDefault(chatId, ChatState.NONE);
        if ("/start".equalsIgnoreCase(userText.trim()) || state == null && !chatMemory.containsKey(chatId)) {
            sendMenu(chatId);
            chatState.put(chatId, ChatState.NONE);
            return;
        }

        // Handle simple menu choices and states
        if (userText.trim().equalsIgnoreCase("1") || userText.toLowerCase().contains("crear ruta")) {
            telegram.sendMessage(chatId, "OK — envía el texto que quieras indexar como ruta.");
            chatState.put(chatId, ChatState.AWAIT_CREATE);
            return;
        }

        if (userText.trim().equalsIgnoreCase("2") || userText.toLowerCase().contains("buscar similitud")) {
            telegram.sendMessage(chatId, "OK — envía el texto para buscar rutas similares.");
            chatState.put(chatId, ChatState.AWAIT_SEARCH);
            return;
        }

        if (state == ChatState.AWAIT_CREATE) {
            handleCreateRoute(chatId, userText);
            chatState.put(chatId, ChatState.NONE);
            sendMenu(chatId);
            return;
        }

        if (state == ChatState.AWAIT_SEARCH) {
            handleSearch(chatId, userText);
            chatState.put(chatId, ChatState.NONE);
            sendMenu(chatId);
            return;
        }

        // Default fallthrough: use Gemini for regular chat messages
        /*
         * String aiResponse = gemini.askGemini(memoryAwarePrompt);
         * 
         * String textToRemember = "User said: " + userText + "\nBot thought: " +
         * aiResponse + "\n";
         * chatMemory.put(chatId, truncate(pastConversation + textToRemember));
         * 
         * if (aiResponse.startsWith("TOOL_REQUESTED|")) {
         * String[] parts = aiResponse.split("\\|", 3);
         * String functionName = parts[1];
         * String argumentsJson = parts[2];
         * 
         * log.info("Gemini chose tool={} args={}", functionName, argumentsJson);
         * 
         * BotTool tool = toolsByName.get(functionName);
         * if (tool == null) {
         * telegram.sendMessage(chatId, "I wanted to use the '" + functionName +
         * "' tool, but it doesn't exist.");
         * return;
         * }
         * 
         * try {
         * JsonNode args = mapper.readTree(argumentsJson);
         * Object rawData = tool.execute(args);
         * String jsonData = mapper.writeValueAsString(rawData);
         * String friendly = gemini.summarizeData(userText, jsonData);
         * 
         * chatMemory.put(chatId, truncate(chatMemory.get(chatId) + "Bot replied: " +
         * friendly + "\n"));
         * telegram.sendMessage(chatId, friendly);
         * } catch (Exception e) {
         * log.error("Tool execution failed tool={}", functionName, e);
         * telegram.sendMessage(chatId, "Database error: " + e.getMessage());
         * }
         * } else {
         * telegram.sendMessage(chatId, aiResponse);
         * }
         */
    }

    private void sendMenu(Long chatId) {
        String menu = "Elige una opción:\n1) crear ruta\n2) buscar similitud\nResponde con 1 o 2.";
        telegram.sendMessage(chatId, menu);
    }

    private void handleCreateRoute(Long chatId, String text) {
        try {
            List<float[]> emb = embeddingService.embed(List.of(text));
            float[] vector = emb.get(0);
            if (semanticRepo == null) {
                telegram.sendMessage(chatId, "No hay base de datos de vectores configurada.");
                return;
            }
            int id = semanticRepo.insertRoutePreferVector(text, vector, "telegram");
            telegram.sendMessage(chatId, "Ruta creada con id=" + id);
        } catch (Exception e) {
            log.error("Failed to create route", e);
            telegram.sendMessage(chatId, "Error al crear ruta: " + e.getMessage());
        }
    }

    private void handleSearch(Long chatId, String text) {
        try {
            List<float[]> emb = embeddingService.embed(List.of(text));
            float[] vector = emb.get(0);
            if (semanticRepo == null) {
                telegram.sendMessage(chatId, "No hay base de datos de vectores configurada.");
                return;
            }
            var results = semanticRepo.searchSimilarPreferVector(vector, 1);
            if (results.isEmpty()) {
                telegram.sendMessage(chatId, "No se encontraron rutas similares.");
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Resultados:\n");
            for (var r : results) {
                sb.append("- id=").append(r.getId()).append(" backend=").append(r.getFuncionBackend())
                        .append(" txt=").append(r.getDescripcionTexto()).append("\n");
            }
            telegram.sendMessage(chatId, sb.toString());
        } catch (Exception e) {
            log.error("Search failed", e);
            telegram.sendMessage(chatId, "Error en la búsqueda: " + e.getMessage());
        }
    }

    private String truncate(String s) {
        if (s.length() <= memoryMaxChars)
            return s;
        return s.substring(s.length() - memoryMaxChars);
    }
}
