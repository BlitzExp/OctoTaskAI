package com.octotask.bot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.octotask.bot.tools.BotTool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Walks the registered {@link BotTool} beans and emits the {@code tools[]}
 * payload Gemini's function-calling API expects. Adding a new tool = adding
 * one @Component implementing BotTool; this builder picks it up automatically.
 */
@Component
public class GeminiToolSchemaBuilder {

    private final List<BotTool> tools;
    private final ObjectMapper mapper;

    public GeminiToolSchemaBuilder(List<BotTool> tools, ObjectMapper mapper) {
        this.tools = tools;
        this.mapper = mapper;
    }

    /** Returns an ArrayNode ready to assign to the {@code tools} field. */
    public ArrayNode buildToolsArray() {
        ArrayNode toolsArray = mapper.createArrayNode();
        ObjectNode wrapper = toolsArray.addObject();
        ArrayNode functionDeclarations = wrapper.putArray("functionDeclarations");
        for (BotTool tool : tools) {
            ObjectNode fn = functionDeclarations.addObject();
            fn.put("name", tool.getName());
            fn.put("description", tool.getDescription());
            ObjectNode params = fn.putObject("parameters");
            tool.buildParameters(params);
        }
        return toolsArray;
    }
}
