package com.vincent.inventoryservice.cache;

import com.vincent.inventoryservice.config.InventoryProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Redis-backed available stock counter — Redis-first path for high-concurrency reservation.
 *
 * <p>DB sync happens in the same transaction today; TODO: async batch flush to reduce write amplification.
 */
@Component
public class InventoryRedisCache {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> decrementStockScript;
    private final Duration ttl;
    private final int ttlJitterMaxSeconds;

    public InventoryRedisCache(
            StringRedisTemplate redisTemplate,
            DefaultRedisScript<Long> decrementStockScript,
            InventoryProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.decrementStockScript = decrementStockScript;
        this.ttl = properties.cacheTtl();
        this.ttlJitterMaxSeconds = properties.ttlJitterMaxSeconds();
    }

    public String cacheKey(String productCode) {
        return InventoryRedisKeys.product(productCode);
    }

    public Optional<Integer> getAvailable(String productCode) {
        return RedisSafeExecutor.optional(() -> redisTemplate.opsForValue().get(cacheKey(productCode)))
                .flatMap(value -> value == null ? Optional.empty() : Optional.of(Integer.parseInt(value)));
    }

    public void putAvailable(String productCode, int available) {
        Duration effectiveTtl = RedisTtlJitter.apply(ttl, ttlJitterMaxSeconds);
        RedisSafeExecutor.run(() ->
                redisTemplate.opsForValue().set(cacheKey(productCode), String.valueOf(available), effectiveTtl)
        );
    }

    /**
     * @return new available after decrement, or empty if insufficient / cache miss
     */
    public Optional<Integer> tryAtomicDecrement(String productCode, int quantity) {
        return RedisSafeExecutor.optional(() -> redisTemplate.execute(
                decrementStockScript,
                List.of(cacheKey(productCode)),
                String.valueOf(quantity)
        )).flatMap(result -> {
            if (result == -2L) {
                return Optional.empty();
            }
            if (result == -1L) {
                return Optional.of(-1);
            }
            return Optional.of(result.intValue());
        });
    }

    public void incrementAvailable(String productCode, int quantity) {
        RedisSafeExecutor.run(() -> redisTemplate.opsForValue().increment(cacheKey(productCode), quantity));
    }

    public void evict(String productCode) {
        RedisSafeExecutor.run(() -> redisTemplate.delete(cacheKey(productCode)));
    }
}
