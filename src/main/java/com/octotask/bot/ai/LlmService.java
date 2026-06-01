package com.octotask.bot.ai;

/**
 * A local generative LLM used for two jobs:
 *   1. extracting structured arguments (slot filling) from free user text, and
 *   2. phrasing raw database results into a friendly Telegram reply.
 *
 * Implementations are expected to be best-effort: when the model is offline,
 * {@link #isAvailable()} returns false and {@link #generate} returns null so
 * callers can fall back to templated text. The bot must keep working without it.
 */
public interface LlmService {

    /** True if the backing model is reachable and enabled. */
    boolean isAvailable();

    /**
     * Generate a completion. Returns null on any failure (never throws) so the
     * caller can fall back gracefully.
     */
    String generate(String systemPrompt, String userPrompt);
}
