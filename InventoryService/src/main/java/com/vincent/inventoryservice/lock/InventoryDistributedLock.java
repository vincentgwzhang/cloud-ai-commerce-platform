package com.vincent.inventoryservice.lock;

import com.vincent.inventoryservice.cache.InventoryRedisKeys;
import com.vincent.inventoryservice.cache.RedisSafeExecutor;
import com.vincent.inventoryservice.config.InventoryProperties;
import com.vincent.inventoryservice.observability.InventoryLockMetrics;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Basic Redis distributed lock (SET NX + token) — reduces cache breakdown / thundering herd.
 * Educational POC, not Redlock.
 */
@Component
public class InventoryDistributedLock {

    private final StringRedisTemplate redisTemplate;
    private final Duration lockTtl;
    private final InventoryLockMetrics lockMetrics;

    public InventoryDistributedLock(
            StringRedisTemplate redisTemplate,
            InventoryProperties properties,
            InventoryLockMetrics lockMetrics
    ) {
        this.redisTemplate = redisTemplate;
        this.lockTtl = properties.lockTtl();
        this.lockMetrics = lockMetrics;
    }

    public String tryLock(String productCode) {
        return tryLockKey(InventoryRedisKeys.lock(productCode));
    }

    public void release(String productCode, String token) {
        releaseKey(InventoryRedisKeys.lock(productCode), token);
    }

    public String tryLockKey(String lockKey) {
        String token = UUID.randomUUID().toString();
        boolean acquired = RedisSafeExecutor.optional(() ->
                redisTemplate.opsForValue().setIfAbsent(lockKey, token, lockTtl)
        ).map(Boolean.TRUE::equals).orElse(false);
        if (acquired) {
            lockMetrics.recordAcquired();
        } else {
            lockMetrics.recordFailed();
        }
        return acquired ? token : null;
    }

    public void releaseKey(String lockKey, String token) {
        if (token == null) {
            return;
        }
        RedisSafeExecutor.run(() -> {
            String current = redisTemplate.opsForValue().get(lockKey);
            if (token.equals(current)) {
                redisTemplate.delete(lockKey);
            }
        });
    }
}
