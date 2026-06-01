package com.octotask.bot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.octotask.bot.orchestrator.BotOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/telegram")
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);

    private final BotOrchestrator orchestrator;

    @Value("${telegram.webhook.secret:}")
    private String webhookSecret;

    public TelegramWebhookController(BotOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleTelegramUpdate(
            @RequestBody JsonNode payload,
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken) {
        // If a secret is configured, reject requests that don't carry the matching token.
        if (webhookSecret != null && !webhookSecret.isBlank() && !webhookSecret.equals(secretToken)) {
            log.warn("Rejected webhook call with missing/invalid secret token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unauthorized");
        }
        if (payload.has("message") && payload.get("message").has("text")) {
            Long chatId = payload.get("message").get("chat").get("id").asLong();
            String userText = payload.get("message").get("text").asText();
            orchestrator.processIncomingMessage(chatId, userText);
        } else {
            log.debug("Ignored payload without message.text");
        }
        return ResponseEntity.ok("OK");
    }
}
