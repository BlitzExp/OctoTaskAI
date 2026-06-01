package com.octotask.bot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * One bot capability. Each implementation declares its own parameter schema so
 * adding a new tool means dropping in one class — the semantic router maps a
 * message to a tool name and the argument resolver fills these parameters.
 */
public interface BotTool {

    /** Tool name; must match the funcion_backend stored on a semantic route. */
    String getName();

    /** Human-readable description of what the tool does. */
    String getDescription();

    /**
     * Populate the JSON Schema {@code parameters} object (type, properties,
     * required). The argument resolver reads this to know which fields to fill
     * from the logged-in identity and which to extract from the user's text.
     */
    void buildParameters(ObjectNode parameters);

    /** Execute the tool with resolved arguments. */
    Object execute(JsonNode arguments) throws Exception;
}
