package com.vincent.orderservice.cache;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.vincent.orderservice.config.OrderCacheProperties;
import com.vincent.orderservice.dto.OrderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Idempotency via Redis SETNX on client requestId — safe for duplicate HTTP posts and Kafka replays.
 */
@Component
public class OrderIdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(OrderIdempotencyStore.class);
    private static final String PROCESSING = "PROCESSING";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final OrderCacheMetrics cacheMetrics;
    private final Duration ttl;
    private final int ttlJitterMaxSeconds;

    public OrderIdempotencyStore(
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            OrderCacheMetrics cacheMetrics,
            OrderCacheProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.cacheMetrics = cacheMetrics;
        this.ttl = properties.idempotencyTtl();
        this.ttlJitterMaxSeconds = properties.ttlJitterMaxSeconds();
    }

    public Optional<OrderResponse> findPreviousResult(String requestId) {
        Optional<String> json = read(OrderRedisKeys.request(requestId));
        if (json.isEmpty() || PROCESSING.equals(json.get())) {
            return Optional.empty();
        }
        try {
            log.info("Duplicate order requestId={}, returning cached result", requestId);
            cacheMetrics.recordIdempotencyDuplicate();
            return Optional.of(jsonMapper.readValue(json.get(), OrderResponse.class));
        } catch (JacksonException ex) {
            RedisSafeExecutor.run(() -> redisTemplate.delete(OrderRedisKeys.request(requestId)));
            return Optional.empty();
        }
    }

    public boolean tryClaim(String requestId) {
        Duration effectiveTtl = RedisTtlJitter.apply(ttl, ttlJitterMaxSeconds);
        boolean claimed = RedisSafeExecutor.optional(() ->
                redisTemplate.opsForValue().setIfAbsent(OrderRedisKeys.request(requestId), PROCESSING, effectiveTtl)
        ).map(Boolean.TRUE::equals).orElse(false);
        if (!claimed) {
            cacheMetrics.recordIdempotencyDuplicate();
            log.warn("Concurrent duplicate order requestId={}", requestId);
        }
        return claimed;
    }

    public void saveResult(String requestId, OrderResponse response) {
        Duration effectiveTtl = RedisTtlJitter.apply(ttl, ttlJitterMaxSeconds);
        RedisSafeExecutor.run(() -> {
            try {
                redisTemplate.opsForValue().set(
                        OrderRedisKeys.request(requestId),
                        jsonMapper.writeValueAsString(response),
                        effectiveTtl
                );
            } catch (JacksonException ex) {
                throw new IllegalStateException("Failed to serialize idempotency payload", ex);
            }
        });
    }

    public void releaseClaim(String requestId) {
        RedisSafeExecutor.run(() -> redisTemplate.delete(OrderRedisKeys.request(requestId)));
    }

    private Optional<String> read(String key) {
        return RedisSafeExecutor.optional(() -> redisTemplate.opsForValue().get(key));
    }
}
