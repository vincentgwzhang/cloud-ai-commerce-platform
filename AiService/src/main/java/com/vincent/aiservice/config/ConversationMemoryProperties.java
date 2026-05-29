package com.vincent.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Conversation memory tuning.
 *
 * @param ttl         sliding expiration for an idle conversation
 * @param maxMessages cap on retained messages per conversation (0 = unbounded)
 */
@ConfigurationProperties(prefix = "app.ai.memory")
public record ConversationMemoryProperties(
        Duration ttl,
        int maxMessages
) {
}
