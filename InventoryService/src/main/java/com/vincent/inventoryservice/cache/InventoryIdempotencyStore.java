package com.vincent.inventoryservice.cache;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.vincent.inventoryservice.config.InventoryProperties;
import com.vincent.inventoryservice.dto.InventoryResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Idempotency via Redis SETNX on requestId.
 * Duplicate reservation requests return the first successful result.
 */
@Component
public class InventoryIdempotencyStore {

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final String keyPrefix;
    private final Duration ttl;

    public InventoryIdempotencyStore(
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            InventoryProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.keyPrefix = properties.idempotencyPrefix();
        this.ttl = properties.idempotencyTtl();
    }

    public Optional<InventoryResponse> findPreviousResult(String requestId) {
        String json = redisTemplate.opsForValue().get(keyPrefix + requestId);
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(jsonMapper.readValue(json, InventoryResponse.class));
        } catch (JacksonException ex) {
            redisTemplate.delete(keyPrefix + requestId);
            return Optional.empty();
        }
    }

    public boolean tryClaim(String requestId) {
        Boolean claimed = redisTemplate.opsForValue().setIfAbsent(keyPrefix + requestId, "PROCESSING", ttl);
        return Boolean.TRUE.equals(claimed);
    }

    public void saveResult(String requestId, InventoryResponse response) {
        try {
            redisTemplate.opsForValue().set(
                    keyPrefix + requestId,
                    jsonMapper.writeValueAsString(response),
                    ttl
            );
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize idempotency payload", ex);
        }
    }

    public void releaseClaim(String requestId) {
        redisTemplate.delete(keyPrefix + requestId);
    }
}
