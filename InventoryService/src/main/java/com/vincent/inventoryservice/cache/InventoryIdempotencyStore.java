package com.vincent.inventoryservice.cache;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.vincent.inventoryservice.config.InventoryProperties;
import com.vincent.inventoryservice.dto.InventoryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Idempotency via Redis SETNX on requestId — safe under at-least-once Kafka delivery.
 */
@Component
public class InventoryIdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(InventoryIdempotencyStore.class);
    private static final String PROCESSING = "PROCESSING";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final InventoryCacheMetrics cacheMetrics;
    private final Duration ttl;
    private final int ttlJitterMaxSeconds;

    public InventoryIdempotencyStore(
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            InventoryCacheMetrics cacheMetrics,
            InventoryProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.cacheMetrics = cacheMetrics;
        this.ttl = properties.idempotencyTtl();
        this.ttlJitterMaxSeconds = properties.ttlJitterMaxSeconds();
    }

    public Optional<InventoryResponse> findPreviousResult(String requestId) {
        Optional<String> json = read(InventoryRedisKeys.request(requestId));
        if (json.isEmpty() || PROCESSING.equals(json.get())) {
            return Optional.empty();
        }
        try {
            log.info("Duplicate inventory requestId={}, returning cached result", requestId);
            cacheMetrics.recordIdempotencyDuplicate();
            return Optional.of(jsonMapper.readValue(json.get(), InventoryResponse.class));
        } catch (JacksonException ex) {
            RedisSafeExecutor.run(() -> redisTemplate.delete(InventoryRedisKeys.request(requestId)));
            return Optional.empty();
        }
    }

    public boolean tryClaim(String requestId) {
        Duration effectiveTtl = RedisTtlJitter.apply(ttl, ttlJitterMaxSeconds);
        boolean claimed = RedisSafeExecutor.optional(() ->
                redisTemplate.opsForValue().setIfAbsent(InventoryRedisKeys.request(requestId), PROCESSING, effectiveTtl)
        ).orElse(false);
        if (!claimed) {
            cacheMetrics.recordIdempotencyDuplicate();
            log.warn("Concurrent duplicate inventory requestId={}", requestId);
        }
        return claimed;
    }

    public void saveResult(String requestId, InventoryResponse response) {
        Duration effectiveTtl = RedisTtlJitter.apply(ttl, ttlJitterMaxSeconds);
        RedisSafeExecutor.run(() -> {
            try {
                redisTemplate.opsForValue().set(
                        InventoryRedisKeys.request(requestId),
                        jsonMapper.writeValueAsString(response),
                        effectiveTtl
                );
            } catch (JacksonException ex) {
                throw new IllegalStateException("Failed to serialize idempotency payload", ex);
            }
        });
    }

    public void releaseClaim(String requestId) {
        RedisSafeExecutor.run(() -> redisTemplate.delete(InventoryRedisKeys.request(requestId)));
    }

    private Optional<String> read(String key) {
        return RedisSafeExecutor.optional(() -> redisTemplate.opsForValue().get(key));
    }
}
