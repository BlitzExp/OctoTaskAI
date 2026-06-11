package com.octotask.bot.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.octotask.bot.ai.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns raw tool/DB output into a human-friendly Telegram reply.
 *
 * <p>Small, conversational results are phrased by the local LLM. Large results
 * (long lists of tasks, big JSON) are rendered by a fast, deterministic
 * formatter and paginated — the local 1.5B model takes far longer than the
 * Telegram-friendly timeout to generate thousands of characters, and prose adds
 * nothing to "here are 40 tasks". The deterministic path also runs whenever the
 * LLM is offline, so the bot always answers.
 */
@Component
public class ReplyComposer {

    private static final Logger log = LoggerFactory.getLogger(ReplyComposer.class);

    private final ObjectMapper mapper;
    private final LlmService llm;

    // Above this serialized-JSON size we skip the LLM and render deterministically.
    // Field defaults keep plain (non-Spring) construction working in tests.
    @Value("${bot.reply.llm-max-chars:1000}")
    private int llmMaxChars = 1000;

    // How many list items to show before collapsing the rest into "…y N más".
    @Value("${bot.reply.list-page-size:10}")
    private int listPageSize = 10;

    public ReplyComposer(ObjectMapper mapper, Optional<LlmService> llm) {
        this.mapper = mapper;
        this.llm = llm.orElse(null);
    }

    public String compose(String userQuestion, Object rawData) {
        return compose(userQuestion, rawData, null);
    }

    public String compose(String userQuestion, Object rawData, String conversationContext) {
        String json;
        try {
            json = mapper.writeValueAsString(rawData);
        } catch (Exception e) {
            json = String.valueOf(rawData);
        }

        // Large/list payloads: skip the slow LLM and render deterministically.
        // This is the fix for the 30s Ollama read-timeouts on big result sets.
        boolean large = json.length() > llmMaxChars
                || (rawData instanceof Collection<?> c && c.size() > listPageSize);

        if (!large && llm != null && llm.isAvailable()) {
            String system = "Eres el asistente de OctoTask en Telegram. " +
                    "Redacta la respuesta final para el usuario en español, clara y breve, " +
                    "fácil de leer en el teléfono (usa viñetas o numeración cuando ayude). " +
                    "REGLAS ESTRICTAS: " +
                    "1) Responde ÚNICAMENTE con la información contenida en los datos JSON proporcionados. " +
                    "2) Usa EXACTAMENTE los nombres, descripciones, números y estados tal como vienen en los datos. " +
                    "3) PROHIBIDO inventar, suponer o añadir información que no esté en los datos. " +
                    "4) PROHIBIDO inventar marcos narrativos como tickets de soporte, confirmaciones de cambios, " +
                    "saludos comerciales o despedidas; limítate a presentar los datos. " +
                    "5) Lista TODOS los registros; no omitas ninguno. " +
                    "6) Si los datos están vacíos, dilo claramente (\"No hay resultados\"). " +
                    "7) No expliques tu formato ni tus reglas. " +
                    "8) Si los datos JSON contienen un campo numérico de identificador de tarea (por ejemplo " +
                    "\"ID\" o \"id\"), inclúyelo explícitamente en la respuesta como \"taskId: <valor>\" " +
                    "junto al registro correspondiente. " +
                    "9) Si los datos contienen 'sprintNumber' y 'sprintID', usa siempre 'sprintNumber' " +
                    "para referirte al sprint (escríbelo como \"Sprint N\") — nunca uses el ID interno.";
            String history = (conversationContext == null || conversationContext.isBlank())
                    ? ""
                    : "Contexto reciente de la conversación:\n" + conversationContext + "\n\n";
            String prompt = history +
                    "El usuario preguntó: \"" + userQuestion + "\".\n" +
                    "Los datos de la base de datos son (JSON):\n" + json + "\n\n" +
                    "Escribe SOLO el mensaje final para el usuario.";
            String out = llm.generate(system, prompt);
            if (out != null && !out.isBlank())
                return out.trim();
            log.debug("LLM phrasing unavailable; using deterministic formatter");
        } else if (large) {
            log.debug("Large payload ({} chars); using deterministic formatter, skipping LLM", json.length());
        }
        return templated(rawData, json);
    }

    /** Fast, mobile-friendly rendering: numbered list, paginated, no LLM. */
    private String templated(Object rawData, String json) {
        try {
            JsonNode node = mapper.valueToTree(rawData);
            if (node.isArray()) {
                int total = node.size();
                if (total == 0)
                    return "No encontré resultados.";
                StringBuilder sb = new StringBuilder();
                sb.append("📋 ").append(total).append(total == 1 ? " resultado:" : " resultados:").append("\n");
                int shown = Math.min(total, listPageSize);
                for (int i = 0; i < shown; i++) {
                    sb.append(i + 1).append(". ").append(renderItem(node.get(i))).append("\n");
                }
                if (total > shown) {
                    sb.append("…y ").append(total - shown).append(" más. ")
                            .append("Puedes acotar (p. ej. \"mis tareas del sprint 3\").");
                }
                return sb.toString().trim();
            }
            if (node.isObject()) {
                return renderItem(node);
            }
            return node.asText(json);
        } catch (Exception e) {
            return json;
        }
    }

    private String renderItem(JsonNode item) {
        if (!item.isObject())
            return item.asText();

        // Task/named record: "#12 Arreglar login — descripción (prioridad 1 · sprint 3)"
        if (item.hasNonNull("name")) {
            StringBuilder s = new StringBuilder();
            String id = firstText(item, "ID", "id");
            if (id != null)
                s.append("#").append(id).append(" ");
            s.append(item.get("name").asText());
            String desc = firstText(item, "description");
            if (desc != null && !desc.isBlank())
                s.append(" — ").append(trimTo(desc, 80));
            List<String> tags = new ArrayList<>();
            if (item.has("priorityID") && item.get("priorityID").asInt() > 0)
                tags.add("prioridad " + item.get("priorityID").asInt());
            if (item.has("sprintNumber") && item.get("sprintNumber").asInt() > 0)
                tags.add("sprint " + item.get("sprintNumber").asInt());
            if (!tags.isEmpty())
                s.append(" (").append(String.join(" · ", tags)).append(")");
            return s.toString();
        }

        // Generic object: compact, non-null key=value, ID relabelled as taskId.
        StringBuilder sb = new StringBuilder();
        Iterator<Map.Entry<String, JsonNode>> it = item.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (e.getValue() == null || e.getValue().isNull())
                continue;
            if (sb.length() > 0)
                sb.append(", ");
            String key = e.getKey();
            if ("ID".equals(key) || "id".equals(key))
                key = "taskId";
            sb.append(key).append("=").append(e.getValue().asText());
        }
        return sb.toString();
    }

    /** First non-null textual value among the given field names, or null. */
    private static String firstText(JsonNode item, String... keys) {
        for (String k : keys) {
            if (item.hasNonNull(k))
                return item.get(k).asText();
        }
        return null;
    }

    private static String trimTo(String s, int max) {
        s = s.trim();
        return s.length() <= max ? s : s.substring(0, max - 1).trim() + "…";
    }
}
