package com.octotask.bot.orchestrator;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-chat state for an in-progress intent clarification. When the semantic
 * router is unsure which tool a message maps to, the bot asks the user and
 * remembers (a) the original message text — which still carries the arguments —
 * and (b) the candidate tools it offered. The user's next reply is interpreted
 * against this.
 *
 * <p>Two shapes:
 * <ul>
 *   <li>one candidate → a yes/no confirmation of a low-confidence guess;</li>
 *   <li>two candidates → a "1 or 2" choice between ambiguous intents.</li>
 * </ul>
 *
 * In-memory only: reset on restart and cleared on /logout. Distinct from
 * {@link PendingActionStore}, which fills missing fields of an already-chosen
 * tool — this one chooses the tool in the first place.
 */
@Component
public class PendingClarificationStore {

    public static final class Pending {
        /** The user's original message, replayed once the tool is chosen. */
        public final String originalText;
        /** Candidate tool names: size 1 = confirm, size 2 = choose. */
        public final List<String> candidateTools;

        public Pending(String originalText, List<String> candidateTools) {
            this.originalText = originalText;
            this.candidateTools = List.copyOf(candidateTools);
        }
    }

    private final Map<Long, Pending> byChat = new ConcurrentHashMap<>();

    public boolean has(long chatId) { return byChat.containsKey(chatId); }

    public Pending get(long chatId) { return byChat.get(chatId); }

    public void put(long chatId, Pending pending) { byChat.put(chatId, pending); }

    public void clear(long chatId) { byChat.remove(chatId); }
}
