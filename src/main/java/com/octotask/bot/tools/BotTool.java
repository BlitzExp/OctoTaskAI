package com.octotask.bot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * One bot capability. Each implementation declares its own Gemini
 * function-call schema so adding a new tool means dropping in one class.
 */
public interface BotTool {

    /** Tool name (must match the function name Gemini will return). */
    String getName();

    /** Human-readable description Gemini uses to decide when to call. */
    String getDescription();

    /**
     * Populate the JSON Schema {@code parameters} object Gemini expects.
     * Implementations mutate the passed-in ObjectNode (set type, properties,
     * required) and the caller wraps it into the full functionDeclarations
     * payload.
     */
    void buildParameters(ObjectNode parameters);

    /** Execute the tool with arguments parsed from Gemini's response. */
    Object execute(JsonNode arguments) throws Exception;
}
