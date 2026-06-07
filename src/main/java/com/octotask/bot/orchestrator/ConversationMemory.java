package com.octotask.bot.orchestrator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-chat ephemeral conversation memory: a rolling context window of the most
 * recent user/bot turns, bounded to the last {@code bot.memory.max-chars}
 * characters. It is fed into the local LLM prompts (slot extraction + reply
 * phrasing) so the bot can resolve follow-up references — e.g. after it asks
 * "¿para qué sprint?" the user can just reply "el 3" — and phrase coherent
 * answers.
 *
 * In-memory only: reset on restart and cleared on /logout. This restores the
 * conversation memory the original (pre local-only rewrite) orchestrator had,
 * which is why {@code bot.memory.max-chars} already existed in configuration.
 */
@Component
public class ConversationMemory {

    /** chatId -> rolling, newest-trimmed conversation context. */
    private final Map<Long, String> byChat = new ConcurrentHashMap<>();

    // Field default keeps plain (non-Spring) construction working in tests;
    // Spring overrides it from bot.memory.max-chars.
    @Value("${bot.memory.max-chars:200}")
    private int maxChars = 200;

    /** Recent conversation context for a chat, or "" if there is none yet. */
    public String recall(long chatId) {
        return byChat.getOrDefault(chatId, "");
    }

    /** Record what the user said. */
    public void recordUser(long chatId, String text) {
        append(chatId, "Usuario: " + safe(text) + "\n");
    }

    /** Record what the bot replied. */
    public void recordBot(long chatId, String text) {
        append(chatId, "Bot: " + safe(text) + "\n");
    }

    /** Forget everything for a chat (e.g. on /logout). */
    public void clear(long chatId) {
        byChat.remove(chatId);
    }

    private void append(long chatId, String turn) {
        if (turn.isBlank()) return;
        String updated = byChat.getOrDefault(chatId, "") + turn;
        byChat.put(chatId, truncate(updated));
    }

    /** Keep only the most recent {@code maxChars} characters. */
    private String truncate(String s) {
        if (maxChars <= 0 || s.length() <= maxChars) return s;
        return s.substring(s.length() - maxChars);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
