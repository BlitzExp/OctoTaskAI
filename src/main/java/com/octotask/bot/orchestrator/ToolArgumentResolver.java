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
 * 1. Read the tool's own parameter schema (it already declares its fields).
 * 2. Inject identity-derived fields by naming convention
 * (userName, teamId, assigneeId) so the user never has to state who they are.
 * 3. Ask the local LLM to extract any remaining fields from the message.
 * 4. Report any required field still missing so the orchestrator can ask.
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
        return resolve(tool, identity, userText, null, null);
    }

    public Resolution resolve(BotTool tool, BotUserLink identity, String userText, String conversationContext) {
        return resolve(tool, identity, userText, conversationContext, null);
    }

    /**
     * @param seedArgs arguments already collected on a previous turn (multi-turn
     *                 slot filling). Fields already present are kept and not
     *                 re-extracted; only the still-missing ones are pulled from
     *                 the latest {@code userText}. Pass {@code null} for a fresh
     *                 start.
     */
    public Resolution resolve(BotTool tool, BotUserLink identity, String userText,
            String conversationContext, ObjectNode seedArgs) {
        ObjectNode schema = mapper.createObjectNode();
        tool.buildParameters(schema);
        JsonNode properties = schema.path("properties");
        List<String> required = new ArrayList<>();
        if (schema.has("required")) {
            schema.get("required").forEach(n -> required.add(n.asText()));
        }

        // Start from anything we already collected on prior turns, so the user
        // doesn't have to repeat fields they've already given.
        ObjectNode args = seedArgs != null ? seedArgs.deepCopy() : mapper.createObjectNode();

        // 1) identity injection by convention
        injectIdentity(args, properties, identity);

        // 2) figure out which fields still need values from the text
        List<String> toExtract = new ArrayList<>();
        Iterator<String> fields = properties.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!args.has(field))
                toExtract.add(field);
        }

        // 3) LLM extraction for the remaining fields
        if (!toExtract.isEmpty() && llm != null && llm.isAvailable()) {
            extractWithLlm(args, properties, toExtract, userText, conversationContext);
        }

        // 3b) Fallback: if required numeric fields are still missing, try to
        // parse them directly from the user's latest message (e.g. user replies
        // with "42"). This helps when the LLM is unavailable or fails to
        // extract simple numeric answers during slot filling.
        if (!toExtract.isEmpty()) {
            parseNumericFallback(args, properties, toExtract, userText);
        }

        // 4) which required fields are still missing?
        List<String> missing = new ArrayList<>();
        for (String req : required) {
            if (!args.has(req) || args.get(req).isNull())
                missing.add(req);
        }
        return new Resolution(args, missing);
    }

    /**
     * Try to find integer/number values in the user's text for still-missing
     * numeric fields and inject them into args.
     */
    private void parseNumericFallback(ObjectNode args, JsonNode properties, List<String> fields, String userText) {
        if (userText == null || userText.isBlank())
            return;
        // find first integer and first decimal number
        java.util.regex.Matcher intMatcher = java.util.regex.Pattern.compile("(-?\\d+)").matcher(userText);
        java.util.regex.Matcher numMatcher = java.util.regex.Pattern.compile("(-?\\d+(?:\\.\\d+)?)/").matcher(userText);
        Integer foundInt = null;
        Double foundNum = null;
        if (intMatcher.find()) {
            try {
                foundInt = Integer.parseInt(intMatcher.group(1));
            } catch (Exception ignored) {
            }
        }
        if (foundNum == null) {
            java.util.regex.Matcher dm = java.util.regex.Pattern.compile("(-?\\d+(?:\\.\\d+)?)").matcher(userText);
            if (dm.find()) {
                try {
                    foundNum = Double.parseDouble(dm.group(1));
                } catch (Exception ignored) {
                }
            }
        }
        for (String f : fields) {
            if (args.has(f))
                continue;
            JsonNode prop = properties.get(f);
            String type = prop.path("type").asText("STRING");
            try {
                switch (type) {
                    case "INTEGER" -> {
                        if (foundInt != null)
                            args.put(f, foundInt);
                    }
                    case "NUMBER" -> {
                        if (foundNum != null)
                            args.put(f, foundNum);
                    }
                }
            } catch (Exception e) {
                log.debug("Numeric fallback parse failed for field {}: {}", f, e.getMessage());
            }
        }
    }

    private void injectIdentity(ObjectNode args, JsonNode properties, BotUserLink id) {
        if (id == null)
            return;
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

    private void extractWithLlm(ObjectNode args, JsonNode properties, List<String> fields,
            String userText, String conversationContext) {
        StringBuilder fieldSpec = new StringBuilder();
        for (String f : fields) {
            JsonNode prop = properties.get(f);
            String type = prop.path("type").asText("STRING");
            String desc = prop.path("description").asText("");
            fieldSpec.append("- ").append(f).append(" (").append(type).append("): ").append(desc).append("\n");
        }
        String system = "You extract structured fields from a user's message. " +
                "Use the prior conversation only to resolve references in the latest message " +
                "(e.g. a value the bot just asked for). " +
                "Return ONLY a minified JSON object with the requested fields. " +
                "STRICT RULES: " +
                "Include a field ONLY if the user EXPLICITLY stated its value. " +
                "If a value was not given, OMIT the field entirely — never guess, never infer. " +
                "NEVER use a word from the command itself as a value: e.g. for 'crea una tarea' the " +
                "task name is NOT 'tarea'/'task'/'una tarea' — it is simply missing. " +
                "Use numbers for INTEGER/NUMBER fields.\n" +
                "Examples:\n" +
                "Fields: name (STRING), sprintId (INTEGER)\n" +
                "User message: \"crea una tarea\"\nJSON: {}\n" +
                "Fields: name (STRING), sprintId (INTEGER), priority (INTEGER)\n" +
                "User message: \"crea una tarea llamada Arreglar login en el sprint 3 prioridad 1\"\n" +
                "JSON: {\"name\":\"Arreglar login\",\"sprintId\":3,\"priority\":1}\n" +
                "Fields: name (STRING), sprintId (INTEGER)\n" +
                "User message: \"sprint 5 y prioridad 2\"\nJSON: {\"sprintId\":5}";
        String history = (conversationContext == null || conversationContext.isBlank())
                ? ""
                : "Prior conversation:\n" + conversationContext + "\n";
        String prompt = history + "Fields to extract:\n" + fieldSpec +
                "\nUser message: \"" + userText + "\"\nJSON:";

        String raw = llm.generate(system, prompt);
        if (raw == null)
            return;
        JsonNode parsed = parseJsonObject(raw);
        if (parsed == null)
            return;

        for (String f : fields) {
            if (!parsed.has(f) || parsed.get(f).isNull())
                continue;
            JsonNode value = parsed.get(f);
            String type = properties.get(f).path("type").asText("STRING");
            switch (type) {
                case "INTEGER" -> {
                    if (value.canConvertToInt())
                        args.put(f, value.asInt());
                    else if (value.isTextual() && value.asText().matches("-?\\d+"))
                        args.put(f, Integer.parseInt(value.asText()));
                }
                case "NUMBER" -> {
                    if (value.isNumber())
                        args.put(f, value.asDouble());
                    else if (value.isTextual() && value.asText().matches("-?\\d+(\\.\\d+)?"))
                        args.put(f, Double.parseDouble(value.asText()));
                }
                default -> {
                    String text = value.asText();
                    // Safety net: a small model sometimes echoes the command verb as the
                    // task name ("crea una tarea" -> name="tarea"). Reject such generic
                    // values so the field stays missing and the bot asks for it.
                    if (isGenericNoise(text)) {
                        log.debug("Dropping generic extracted value for field '{}': '{}'", f, text);
                    } else {
                        args.put(f, text);
                    }
                }
            }
        }
    }

    private static final java.util.Set<String> GENERIC_NOISE = java.util.Set.of(
            "tarea", "tareas", "una tarea", "nueva tarea", "la tarea",
            "task", "a task", "new task", "the task",
            "crear una tarea", "crea una tarea", "crear tarea", "crea tarea",
            "una nueva tarea", "crear una nueva tarea", "crea una nueva tarea",
            "agregar tarea", "agrega tarea", "agrega una tarea", "agregar una tarea");

    private static final java.util.regex.Pattern CREATE_TASK_VERB =
            java.util.regex.Pattern.compile("(crea[r]?|agrega[r]?|add|creat[e]?)\\b.*\\b(tarea[s]?|task[s]?)\\b.*");

    /**
     * True if the text is just a filler/command word, not a real user-supplied value.
     * Prevents small LLMs from echoing the command phrase (e.g. "crea una tarea") as
     * the task name when no explicit name was given.
     */
    private static boolean isGenericNoise(String text) {
        if (text == null)
            return true;
        String t = text.trim().toLowerCase();
        if (t.isEmpty() || GENERIC_NOISE.contains(t))
            return true;
        // Catch LLM variants not in the exact set: "crea[r]/agrega[r]/add/create ... tarea/task"
        return CREATE_TASK_VERB.matcher(t).matches();
    }

    /** Pull the first {...} block out of an LLM response and parse it. */
    private JsonNode parseJsonObject(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start)
            return null;
        String json = raw.substring(start, end + 1);
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            log.debug("Could not parse LLM extraction JSON: {}", json);
            return null;
        }
    }
}
