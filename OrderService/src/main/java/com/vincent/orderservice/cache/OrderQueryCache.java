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
    private final Duration ttl;
    private final int ttlJitterMaxSeconds;

    public OrderQueryCache(
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            OrderCacheProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.ttl = properties.orderCacheTtl();
        this.ttlJitterMaxSeconds = properties.ttlJitterMaxSeconds();
    }

    public Optional<OrderResponse> get(String orderNo) {
        return read(OrderRedisKeys.detail(orderNo)).flatMap(json -> {
            try {
                return Optional.of(jsonMapper.readValue(json, OrderResponse.class));
            } catch (JacksonException ex) {
                RedisSafeExecutor.run(() -> redisTemplate.delete(OrderRedisKeys.detail(orderNo)));
                return Optional.empty();
            }
        });
    }

    public void put(OrderResponse response) {
        Duration effectiveTtl = RedisTtlJitter.apply(ttl, ttlJitterMaxSeconds);
        RedisSafeExecutor.run(() -> {
            try {
                redisTemplate.opsForValue().set(
                        OrderRedisKeys.detail(response.orderNo()),
                        jsonMapper.writeValueAsString(response),
                        effectiveTtl
                );
            } catch (JacksonException ex) {
                throw new IllegalStateException("Failed to cache order", ex);
            }
        });
    }

    public void evict(String orderNo) {
        RedisSafeExecutor.run(() -> redisTemplate.delete(OrderRedisKeys.detail(orderNo)));
    }

    private Optional<String> read(String key) {
        return RedisSafeExecutor.optional(() -> redisTemplate.opsForValue().get(key));
    }
}
