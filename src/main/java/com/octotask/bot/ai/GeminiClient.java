package com.octotask.bot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;
    private final GeminiToolSchemaBuilder toolSchemaBuilder;

    public GeminiClient(RestTemplate restTemplate, ObjectMapper mapper, GeminiToolSchemaBuilder toolSchemaBuilder) {
        this.restTemplate = restTemplate;
        this.mapper = mapper;
        this.toolSchemaBuilder = toolSchemaBuilder;
    }

    /**
     * Ask Gemini what to do with the user's text. Returns either:
     *   - "TOOL_REQUESTED|<name>|<argsJson>" if Gemini wants to invoke a tool, or
     *   - the plain text reply otherwise.
     */
    public String askGemini(String promptText) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.putArray("contents").addObject().putArray("parts").addObject().put("text", promptText);
            body.set("tools", toolSchemaBuilder.buildToolsArray());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> req = new HttpEntity<>(body.toString(), headers);

            JsonNode response = restTemplate.postForObject(apiUrl + apiKey, req, JsonNode.class);
            if (response != null && response.has("candidates")) {
                JsonNode part = response.get("candidates").get(0).get("content").get("parts").get(0);
                if (part.has("functionCall")) {
                    String name = part.get("functionCall").get("name").asText();
                    String args = part.get("functionCall").get("args").toString();
                    return "TOOL_REQUESTED|" + name + "|" + args;
                }
                if (part.has("text")) {
                    return part.get("text").asText();
                }
            }
            return "Sorry, I couldn't process that.";
        } catch (Exception e) {
            log.error("Gemini askGemini failed", e);
            return "My AI brain is currently offline. Error: " + e.getMessage();
        }
    }

    /** Turn raw DB data into a user-friendly Telegram reply. */
    public String summarizeData(String userOriginalQuestion, String rawDatabaseData) {
        try {
            String prompt =
                "You are a strict data-reporting Telegram bot. " +
                "The user asked: '" + userOriginalQuestion + "'. " +
                "The database returned this raw data: " + rawDatabaseData + ". " +
                "Draft the exact, final text message to send back to the user. " +
                "STRICT RULES: \n" +
                "1. EXACT MATCH: You must use the EXACT task names, descriptions, and statuses exactly as they appear in the raw data. Do NOT reword, paraphrase, or summarize them.\n" +
                "2. If the data is STATISTICS or KPIs, act like a data analyst. Format it beautifully with bullet points, and short, insightful summaries of the numbers.\n" +
                "3. NO TRUNCATION: You must list EVERY SINGLE item provided in the raw data. If the database returns 32 items, you must list all 32. Do NOT stop at 10, do NOT say 'and more', do NOT skip any data.\n" +
                "4. NO META-TEXT: Do not provide multiple options or explain your formatting.\n" +
                "5. FORMATTING: Output ONLY the final response. Format it clearly using bullet points or numbers so it is easy to read on a mobile phone.";

            ObjectNode body = mapper.createObjectNode();
            body.putArray("contents").addObject().putArray("parts").addObject().put("text", prompt);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> req = new HttpEntity<>(body.toString(), headers);

            JsonNode response = restTemplate.postForObject(apiUrl + apiKey, req, JsonNode.class);
            if (response != null && response.has("candidates")) {
                return response.get("candidates").get(0).get("content").get("parts").get(0).get("text").asText();
            }
            return "Here is your raw data: " + rawDatabaseData;
        } catch (Exception e) {
            log.error("Gemini summarizeData failed", e);
            return "I got the data, but my language center crashed. Raw data: " + rawDatabaseData;
        }
    }
}
