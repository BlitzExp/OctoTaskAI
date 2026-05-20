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

    @Value("${bot.memory.max-chars:200}")
    private int memoryMaxChars;

    /** Per-chat ephemeral context window. Reset on restart. */
    private final Map<Long, String> chatMemory = new ConcurrentHashMap<>();

    public BotOrchestrator(GeminiClient gemini,
                           TelegramClient telegram,
                           List<BotTool> tools,
                           ObjectMapper mapper) {
        this.gemini = gemini;
        this.telegram = telegram;
        this.mapper = mapper;
        this.toolsByName = tools.stream().collect(Collectors.toMap(BotTool::getName, Function.identity()));
    }

    public void processIncomingMessage(Long chatId, String userText) {
        log.info("Incoming chatId={} text={}", chatId, userText);

        String pastConversation = chatMemory.getOrDefault(chatId, "");
        String memoryAwarePrompt = "Previous Conversation Context:\n" + pastConversation +
                "\n\nThe user's newest message is: '" + userText + "'";

        String aiResponse = gemini.askGemini(memoryAwarePrompt);

        String textToRemember = "User said: " + userText + "\nBot thought: " + aiResponse + "\n";
        chatMemory.put(chatId, truncate(pastConversation + textToRemember));

        if (aiResponse.startsWith("TOOL_REQUESTED|")) {
            String[] parts = aiResponse.split("\\|", 3);
            String functionName = parts[1];
            String argumentsJson = parts[2];

            log.info("Gemini chose tool={} args={}", functionName, argumentsJson);

            BotTool tool = toolsByName.get(functionName);
            if (tool == null) {
                telegram.sendMessage(chatId, "I wanted to use the '" + functionName + "' tool, but it doesn't exist.");
                return;
            }

            try {
                JsonNode args = mapper.readTree(argumentsJson);
                Object rawData = tool.execute(args);
                String jsonData = mapper.writeValueAsString(rawData);
                String friendly = gemini.summarizeData(userText, jsonData);

                chatMemory.put(chatId, truncate(chatMemory.get(chatId) + "Bot replied: " + friendly + "\n"));
                telegram.sendMessage(chatId, friendly);
            } catch (Exception e) {
                log.error("Tool execution failed tool={}", functionName, e);
                telegram.sendMessage(chatId, "Database error: " + e.getMessage());
            }
        } else {
            telegram.sendMessage(chatId, aiResponse);
        }
    }

    private String truncate(String s) {
        if (s.length() <= memoryMaxChars) return s;
        return s.substring(s.length() - memoryMaxChars);
    }
}
