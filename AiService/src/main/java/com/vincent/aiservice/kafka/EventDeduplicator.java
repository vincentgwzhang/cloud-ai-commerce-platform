package com.vincent.aiservice.kafka;

import com.vincent.aiservice.cache.AiRedisKeys;
import com.vincent.aiservice.cache.RedisSafeExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Redis-backed idempotency guard for at-least-once Kafka delivery.
 *
 * <p>First sighting of an {@code eventId} wins via {@code SETNX}; redeliveries are skipped. The
 * check is best-effort: if Redis is unavailable we treat the event as new (process it) rather than
 * dropping it — accepting a rare double-count over data loss.
 */
@Component
public class EventDeduplicator {

    private static final Duration DEDUP_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public EventDeduplicator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isDuplicate(String eventId) {
        if (!StringUtils.hasText(eventId)) {
            return false;
        }
        return RedisSafeExecutor.optional(() -> {
            Boolean firstSeen = redisTemplate.opsForValue()
                    .setIfAbsent(AiRedisKeys.dedup(eventId), "1", DEDUP_TTL);
            return !Boolean.TRUE.equals(firstSeen);
        }).orElse(false);
    }
}
