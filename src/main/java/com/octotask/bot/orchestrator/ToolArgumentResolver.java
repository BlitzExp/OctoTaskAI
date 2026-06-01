package com.octotask.bot.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.octotask.bot.ai.LlmService;
import com.octotask.bot.data.BotUserLink;
import com.octotask.bot.tools.BotTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds a tool's argument JSON from the logged-in identity + the user's text.
 *
 * Strategy:
 *   1. Read the tool's own parameter schema (it already declares its fields).
 *   2. Inject identity-derived fields by naming convention
 *      (userName, teamId, assigneeId) so the user never has to state who they are.
 *   3. Ask the local LLM to extract any remaining fields from the message.
 *   4. Report any required field still missing so the orchestrator can ask.
 */
@Component
public class ToolArgumentResolver {

    private static final Logger log = LoggerFactory.getLogger(ToolArgumentResolver.class);

    private final ObjectMapper mapper;
    private final LlmService llm;

    public ToolArgumentResolver(ObjectMapper mapper, Optional<LlmService> llm) {
        this.mapper = mapper;
        this.llm = llm.orElse(null);
    }

    public static class Resolution {
        public final ObjectNode args;
        public final List<String> missingRequired;

        public Resolution(ObjectNode args, List<String> missingRequired) {
            this.args = args;
            this.missingRequired = missingRequired;
        }
    }

    public Resolution resolve(BotTool tool, BotUserLink identity, String userText) {
        ObjectNode schema = mapper.createObjectNode();
        tool.buildParameters(schema);
        JsonNode properties = schema.path("properties");
        List<String> required = new ArrayList<>();
        if (schema.has("required")) {
            schema.get("required").forEach(n -> required.add(n.asText()));
        }

        ObjectNode args = mapper.createObjectNode();

        // 1) identity injection by convention
        injectIdentity(args, properties, identity);

        // 2) figure out which fields still need values from the text
        List<String> toExtract = new ArrayList<>();
        Iterator<String> fields = properties.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!args.has(field)) toExtract.add(field);
        }

        // 3) LLM extraction for the remaining fields
        if (!toExtract.isEmpty() && llm != null && llm.isAvailable()) {
            extractWithLlm(args, properties, toExtract, userText);
        }

        // 4) which required fields are still missing?
        List<String> missing = new ArrayList<>();
        for (String req : required) {
            if (!args.has(req) || args.get(req).isNull()) missing.add(req);
        }
        return new Resolution(args, missing);
    }

    private void injectIdentity(ObjectNode args, JsonNode properties, BotUserLink id) {
        if (id == null) return;
        if (properties.has("userName")) {
            args.put("userName", id.getAppUserName());
        }
        if (properties.has("teamId") && id.getTeamId() != null) {
            args.put("teamId", id.getTeamId());
        }
        if (properties.has("assigneeId")) {
            args.put("assigneeId", id.getAppUserId());
        }
    }

    private void extractWithLlm(ObjectNode args, JsonNode properties, List<String> fields, String userText) {
        StringBuilder fieldSpec = new StringBuilder();
        for (String f : fields) {
            JsonNode prop = properties.get(f);
            String type = prop.path("type").asText("STRING");
            String desc = prop.path("description").asText("");
            fieldSpec.append("- ").append(f).append(" (").append(type).append("): ").append(desc).append("\n");
        }
        String system = "You extract structured fields from a user's message. " +
                "Return ONLY a minified JSON object with the requested fields. " +
                "Omit any field you cannot determine from the message — never guess. " +
                "Use numbers for INTEGER/NUMBER fields.";
        String prompt = "Fields to extract:\n" + fieldSpec + "\nUser message: \"" + userText + "\"\nJSON:";

        String raw = llm.generate(system, prompt);
        if (raw == null) return;
        JsonNode parsed = parseJsonObject(raw);
        if (parsed == null) return;

        for (String f : fields) {
            if (!parsed.has(f) || parsed.get(f).isNull()) continue;
            JsonNode value = parsed.get(f);
            String type = properties.get(f).path("type").asText("STRING");
            switch (type) {
                case "INTEGER" -> {
                    if (value.canConvertToInt()) args.put(f, value.asInt());
                    else if (value.isTextual() && value.asText().matches("-?\\d+")) args.put(f, Integer.parseInt(value.asText()));
                }
                case "NUMBER" -> {
                    if (value.isNumber()) args.put(f, value.asDouble());
                    else if (value.isTextual() && value.asText().matches("-?\\d+(\\.\\d+)?")) args.put(f, Double.parseDouble(value.asText()));
                }
                default -> args.put(f, value.asText());
            }
        }
    }

    /** Pull the first {...} block out of an LLM response and parse it. */
    private JsonNode parseJsonObject(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        String json = raw.substring(start, end + 1);
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            log.debug("Could not parse LLM extraction JSON: {}", json);
            return null;
        }
    }
}
