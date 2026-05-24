package com.vincent.orderservice.cache;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.vincent.orderservice.config.OrderCacheProperties;
import com.vincent.orderservice.dto.OrderResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/** Optional hot-path cache for GET /api/orders/{orderNo}. */
@Component
public class OrderQueryCache {

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final String keyPrefix;
    private final Duration ttl;

    public OrderQueryCache(
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            OrderCacheProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.keyPrefix = properties.orderCachePrefix();
        this.ttl = properties.orderCacheTtl();
    }

    public Optional<OrderResponse> get(String orderNo) {
        String json = redisTemplate.opsForValue().get(keyPrefix + orderNo);
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(jsonMapper.readValue(json, OrderResponse.class));
        } catch (JacksonException ex) {
            redisTemplate.delete(keyPrefix + orderNo);
            return Optional.empty();
        }
    }

    public void put(OrderResponse response) {
        try {
            redisTemplate.opsForValue().set(
                    keyPrefix + response.orderNo(),
                    jsonMapper.writeValueAsString(response),
                    ttl
            );
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to cache order", ex);
        }
    }

    public void evict(String orderNo) {
        redisTemplate.delete(keyPrefix + orderNo);
    }
}
