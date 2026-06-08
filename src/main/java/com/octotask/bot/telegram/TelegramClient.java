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

    private static final int TELEGRAM_CHUNK = 3000;

    public void sendMessage(Long chatId, String textToSend) {
        if (textToSend == null)
            return;
        int length = textToSend.length();
        int offset = 0;
        while (offset < length) {
            int end = Math.min(offset + TELEGRAM_CHUNK, length);
            // If we're cutting in the middle of a word and not at the end of the whole
            // text,
            // extend to include the rest of the current word so the split doesn't break
            // words.
            if (end < length) {
                // If the next char is not whitespace, advance to the next whitespace (end of
                // word)
                if (!Character.isWhitespace(textToSend.charAt(end))) {
                    int ext = end;
                    while (ext < length && !Character.isWhitespace(textToSend.charAt(ext))) {
                        ext++;
                    }
                    end = ext;
                }
            }

            String chunk = textToSend.substring(offset, end);
            sendChunk(chatId, chunk);
            offset = end;
        }
    }

    private void sendChunk(Long chatId, String text) {
        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        try {
            restTemplate.postForObject(url, body, String.class);
            log.info("Telegram message chunk sent chatId={} size={}", chatId, text == null ? 0 : text.length());
        } catch (Exception e) {
            log.error("Telegram sendMessage chunk failed chatId={}", chatId, e);
        }
    }
}
