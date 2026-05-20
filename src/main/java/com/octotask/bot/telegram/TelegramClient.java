package com.octotask.bot.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramClient.class);

    @Value("${telegram.bot.token}")
    private String botToken;

    private final RestTemplate restTemplate;

    public TelegramClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void sendMessage(Long chatId, String textToSend) {
        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", textToSend);
        try {
            restTemplate.postForObject(url, body, String.class);
            log.info("Telegram message sent chatId={}", chatId);
        } catch (Exception e) {
            log.error("Telegram sendMessage failed chatId={}", chatId, e);
        }
    }
}
