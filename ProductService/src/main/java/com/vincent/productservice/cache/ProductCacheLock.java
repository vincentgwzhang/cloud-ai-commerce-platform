package com.vincent.productservice.cache;

import com.vincent.productservice.config.ProductCacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Simple distributed lock via Redis SETNX to reduce cache breakdown on hot keys.
 * Educational POC — not a full Redlock implementation.
 */
@Component
public class ProductCacheLock {

    private final StringRedisTemplate redisTemplate;
    private final Duration lockTtl;

    public ProductCacheLock(StringRedisTemplate redisTemplate, ProductCacheProperties cacheProperties) {
        this.redisTemplate = redisTemplate;
        this.lockTtl = cacheProperties.lockTtl();
    }

    public String tryAcquire(String lockKey) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, token, lockTtl);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    public void release(String lockKey, String token) {
        String current = redisTemplate.opsForValue().get(lockKey);
        if (token != null && token.equals(current)) {
            redisTemplate.delete(lockKey);
        }
    }
}
