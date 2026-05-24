package com.vincent.inventoryservice.cache;

import com.vincent.inventoryservice.config.InventoryProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Redis cache for available stock (cache-aside + write-through after DB commit).
 *
 * Anti-overselling: Lua script atomically checks and decrements so concurrent threads
 * cannot drive the cached counter below zero.
 */
@Component
public class InventoryRedisCache {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> decrementStockScript;
    private final String keyPrefix;
    private final Duration ttl;

    public InventoryRedisCache(
            StringRedisTemplate redisTemplate,
            DefaultRedisScript<Long> decrementStockScript,
            InventoryProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.decrementStockScript = decrementStockScript;
        this.keyPrefix = properties.cacheKeyPrefix();
        this.ttl = properties.cacheTtl();
    }

    public String cacheKey(String productCode) {
        return keyPrefix + productCode;
    }

    public Optional<Integer> getAvailable(String productCode) {
        String value = redisTemplate.opsForValue().get(cacheKey(productCode));
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(value));
    }

    public void putAvailable(String productCode, int available) {
        redisTemplate.opsForValue().set(cacheKey(productCode), String.valueOf(available), ttl);
    }

    /**
     * @return new available after decrement, or empty if insufficient / cache miss
     */
    public Optional<Integer> tryAtomicDecrement(String productCode, int quantity) {
        Long result = redisTemplate.execute(
                decrementStockScript,
                List.of(cacheKey(productCode)),
                String.valueOf(quantity)
        );
        if (result == null || result == -2L) {
            return Optional.empty();
        }
        if (result == -1L) {
            return Optional.of(-1);
        }
        return Optional.of(result.intValue());
    }

    public void incrementAvailable(String productCode, int quantity) {
        redisTemplate.opsForValue().increment(cacheKey(productCode), quantity);
    }

    public void evict(String productCode) {
        redisTemplate.delete(cacheKey(productCode));
    }
}
