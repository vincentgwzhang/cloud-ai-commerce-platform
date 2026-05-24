package com.vincent.inventoryservice.lock;

import com.vincent.inventoryservice.config.InventoryProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Basic Redis distributed lock (SET NX + token).
 * Educational alternative to Redisson — not a production-grade lock implementation.
 */
@Component
public class InventoryDistributedLock {

    private final StringRedisTemplate redisTemplate;
    private final String lockPrefix;
    private final Duration lockTtl;

    public InventoryDistributedLock(StringRedisTemplate redisTemplate, InventoryProperties properties) {
        this.redisTemplate = redisTemplate;
        this.lockPrefix = properties.lockPrefix();
        this.lockTtl = properties.lockTtl();
    }

    public String tryLock(String productCode) {
        String key = lockPrefix + productCode;
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, lockTtl);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    public void release(String productCode, String token) {
        if (token == null) {
            return;
        }
        String key = lockPrefix + productCode;
        String current = redisTemplate.opsForValue().get(key);
        if (token.equals(current)) {
            redisTemplate.delete(key);
        }
    }
}
