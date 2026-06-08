package com.octotask.bot.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.octotask.bot.ai.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Turns raw tool/DB output into a human-friendly Telegram reply. Uses the local
 * LLM when available, and falls back to a deterministic templated rendering so
 * the bot still answers when the LLM is offline.
 */
@Component
public class ReplyComposer {

    private static final Logger log = LoggerFactory.getLogger(ReplyComposer.class);

    private final ObjectMapper mapper;
    private final LlmService llm;

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

        if (llm != null && llm.isAvailable()) {
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
                    "junto al registro correspondiente.";
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
            log.debug("LLM phrasing unavailable; using templated fallback");
        }
        return templated(rawData, json);
    }

    /** Deterministic rendering used when the LLM is down. */
    private String templated(Object rawData, String json) {
        try {
            JsonNode node = mapper.valueToTree(rawData);
            if (node.isArray()) {
                if (node.isEmpty())
                    return "No encontré resultados.";
                StringBuilder sb = new StringBuilder("Resultados (" + node.size() + "):\n");
                int i = 1;
                for (JsonNode item : node) {
                    sb.append(i++).append(". ").append(renderItem(item)).append("\n");
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
        // Prefer a name/title field if present
        if (item.has("name")) {
            String s = item.get("name").asText();
            if (item.has("description") && !item.get("description").isNull()) {
                s += " — " + item.get("description").asText();
            }
            // Append ID if present so users can reference it
            if (item.has("ID")) {
                s += " (taskId:" + item.get("ID").asText() + ")";
            } else if (item.has("id")) {
                s += " (taskId:" + item.get("id").asText() + ")";
            }
            return s;
        }
        StringBuilder sb = new StringBuilder();
        Iterator<Map.Entry<String, JsonNode>> it = item.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (sb.length() > 0)
                sb.append(", ");
            String key = e.getKey();
            // Present ID fields as taskId for clarity
            if ("ID".equals(key) || "id".equals(key))
                key = "taskId";
            sb.append(key).append("=").append(e.getValue().asText());
        }
        return sb.toString();
    }
}
