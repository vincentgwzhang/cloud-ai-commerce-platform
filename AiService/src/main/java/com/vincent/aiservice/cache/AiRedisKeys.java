package com.vincent.aiservice.cache;

/** Centralised Redis key builders for ai-service. */
public final class AiRedisKeys {

    private static final String RECOMMENDATION_PREFIX = "ai:reco:";
    private static final String RECOMMENDATION_LOCK_PREFIX = "ai:reco:lock:";
    private static final String DEDUP_PREFIX = "ai:dedup:";
    private static final String CONVERSATION_PREFIX = "ai:chat:conversation:";

    private AiRedisKeys() {
    }

    public static String conversation(String userId, String conversationId) {
        return CONVERSATION_PREFIX + userId + ":" + conversationId;
    }

    public static String recommendation(String username) {
        return RECOMMENDATION_PREFIX + username;
    }

    public static String recommendationLock(String username) {
        return RECOMMENDATION_LOCK_PREFIX + username;
    }

    public static String dedup(String eventId) {
        return DEDUP_PREFIX + eventId;
    }
}
