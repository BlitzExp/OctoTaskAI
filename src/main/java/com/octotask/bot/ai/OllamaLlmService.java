package com.octotask.bot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * {@link LlmService} backed by a local Ollama server (https://ollama.com).
 * Ollama runs as a companion process/container — fully offline, no API key.
 * Start it with `ollama serve` and `ollama pull <model>`.
 *
 * Uses its own short-timeout RestTemplate so a slow/unreachable model never
 * blocks Telegram or DB calls.
 */
@Service
@ConditionalOnProperty(prefix = "ollama", name = "enabled", havingValue = "true", matchIfMissing = false)
public class OllamaLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(OllamaLlmService.class);

    @Value("${ollama.url:http://localhost:11434}")
    private String baseUrl;

    @Value("${ollama.model:qwen2.5:3b}")
    private String model;

    @Value("${ollama.timeout-ms:30000}")
    private int timeoutMs;

    private final ObjectMapper mapper;
    private RestTemplate http;

    public OllamaLlmService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    private RestTemplate http() {
        if (http == null) {
            SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
            f.setConnectTimeout(3000);
            f.setReadTimeout(timeoutMs);
            http = new RestTemplate(f);
        }
        return http;
    }

    @Override
    public boolean isAvailable() {
        try {
            http().getForObject(baseUrl + "/api/tags", String.class);
            return true;
        } catch (Exception e) {
            log.debug("Ollama not reachable at {}: {}", baseUrl, e.getMessage());
            return false;
        }
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("stream", false);
            ArrayNode messages = body.putArray("messages");
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                ObjectNode sys = messages.addObject();
                sys.put("role", "system");
                sys.put("content", systemPrompt);
            }
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", userPrompt);
            ObjectNode options = body.putObject("options");
            // Greedy, deterministic decoding: a small model is far less likely to
            // confabulate narrative framing when temperature is 0.
            options.put("temperature", 0.0);
            options.put("top_p", 1.0);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> req = new HttpEntity<>(body.toString(), headers);

            JsonNode resp = http().postForObject(baseUrl + "/api/chat", req, JsonNode.class);
            if (resp != null && resp.has("message") && resp.get("message").has("content")) {
                return resp.get("message").get("content").asText();
            }
            log.warn("Ollama returned unexpected payload: {}", resp);
            return null;
        } catch (Exception e) {
            log.warn("Ollama generate failed (model={}): {}", model, e.getMessage());
            return null;
        }
    }
}
