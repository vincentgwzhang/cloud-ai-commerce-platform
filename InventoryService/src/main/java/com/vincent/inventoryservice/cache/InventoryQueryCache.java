package com.vincent.inventoryservice.cache;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.vincent.inventoryservice.config.InventoryProperties;
import com.vincent.inventoryservice.dto.InventoryResponse;
import com.vincent.inventoryservice.lock.InventoryDistributedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Cache-aside for GET inventory with SETNX lock — reduces thundering herd on hot SKUs.
 */
@Component
public class InventoryQueryCache {

    private static final Logger log = LoggerFactory.getLogger(InventoryQueryCache.class);

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final InventoryDistributedLock distributedLock;
    private final LocalHotInventoryCache localHotCache;
    private final InventoryCacheMetrics cacheMetrics;
    private final Duration detailTtl;
    private final int ttlJitterMaxSeconds;

    public InventoryQueryCache(
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            InventoryDistributedLock distributedLock,
            LocalHotInventoryCache localHotCache,
            InventoryCacheMetrics cacheMetrics,
            InventoryProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.distributedLock = distributedLock;
        this.localHotCache = localHotCache;
        this.cacheMetrics = cacheMetrics;
        this.detailTtl = properties.cacheTtl();
        this.ttlJitterMaxSeconds = properties.ttlJitterMaxSeconds();
    }

    public InventoryResponse get(String productCode, Supplier<InventoryResponse> loader) {
        Optional<InventoryResponse> local = localHotCache.get(productCode);
        if (local.isPresent()) {
            cacheMetrics.recordHit();
            return local.get();
        }

        String cacheKey = InventoryRedisKeys.product(productCode) + ":detail";
        Optional<String> cached = readRaw(cacheKey);
        if (cached.isPresent()) {
            cacheMetrics.recordHit();
            return deserialize(cacheKey, cached.get());
        }

        cacheMetrics.recordMiss();
        String lockKey = InventoryRedisKeys.queryLock(productCode);
        String token = distributedLock.tryLockKey(lockKey);
        if (token == null) {
            return waitForPeer(cacheKey, loader);
        }

        try {
            cached = readRaw(cacheKey);
            if (cached.isPresent()) {
                return deserialize(cacheKey, cached.get());
            }
            InventoryResponse loaded = loader.get();
            write(cacheKey, loaded);
            localHotCache.put(productCode, loaded);
            return loaded;
        } finally {
            distributedLock.releaseKey(lockKey, token);
        }
    }

    private InventoryResponse waitForPeer(String cacheKey, Supplier<InventoryResponse> loader) {
        for (int i = 0; i < 5; i++) {
            Optional<String> cached = readRaw(cacheKey);
            if (cached.isPresent()) {
                return deserialize(cacheKey, cached.get());
            }
            try {
                Thread.sleep(50L * (i + 1));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return loader.get();
    }

    private Optional<String> readRaw(String key) {
        return RedisSafeExecutor.optional(() -> redisTemplate.opsForValue().get(key));
    }

    private InventoryResponse deserialize(String key, String json) {
        try {
            return jsonMapper.readValue(json, InventoryResponse.class);
        } catch (JacksonException ex) {
            RedisSafeExecutor.run(() -> redisTemplate.delete(key));
            throw new IllegalStateException("Invalid inventory detail cache", ex);
        }
    }

    private void write(String key, InventoryResponse response) {
        Duration ttl = RedisTtlJitter.apply(detailTtl, ttlJitterMaxSeconds);
        RedisSafeExecutor.run(() -> {
            try {
                redisTemplate.opsForValue().set(key, jsonMapper.writeValueAsString(response), ttl);
            } catch (JacksonException ex) {
                throw new IllegalStateException("Failed to cache inventory detail", ex);
            }
        });
    }
}
