package com.octotask.bot.orchestrator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-chat memory of an action that the bot started but could not finish in one
 * message because required arguments were missing (e.g. create_task needs a name,
 * sprint and priority). While an action is pending, the orchestrator funnels the
 * user's next messages straight into that tool — merging the newly supplied
 * fields — instead of re-routing them through the semantic router. This is what
 * makes multi-turn slot filling work; without it a follow-up like "sprint 5,
 * priority 2" gets misrouted as a brand-new request.
 */
@Component
public class PendingActionStore {

    /** Give up on a half-finished action after this many user replies make no progress. */
    public static final int MAX_ATTEMPTS = 4;

    public static class PendingAction {
        public final String toolName;
        /** Arguments collected so far (identity + previously extracted fields). */
        public final ObjectNode args;
        /** Required fields still missing as of the last resolution. */
        public List<String> missing;
        /** How many user replies we have consumed trying to complete this action. */
        public int attempts;

        public PendingAction(String toolName, ObjectNode args, List<String> missing) {
            this.toolName = toolName;
            this.args = args;
            this.missing = missing;
            this.attempts = 0;
        }
    }

    private final Map<Long, PendingAction> byChat = new ConcurrentHashMap<>();

    public PendingAction get(long chatId) {
        return byChat.get(chatId);
    }

    public boolean has(long chatId) {
        return byChat.containsKey(chatId);
    }

    public void put(long chatId, PendingAction action) {
        byChat.put(chatId, action);
    }

    public void clear(long chatId) {
        byChat.remove(chatId);
    }
}
