package com.vincent.inventoryservice.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Delayed double-delete on query-view keys after write-through stock updates.
 */
@Component
public class InventoryCacheConsistency {

    private static final Logger log = LoggerFactory.getLogger(InventoryCacheConsistency.class);
    private static final Duration DELAY = Duration.ofMillis(500);

    private final StringRedisTemplate redisTemplate;
    private final LocalHotInventoryCache localHotInventoryCache;

    public InventoryCacheConsistency(
            StringRedisTemplate redisTemplate,
            LocalHotInventoryCache localHotInventoryCache
    ) {
        this.redisTemplate = redisTemplate;
        this.localHotInventoryCache = localHotInventoryCache;
    }

    public void invalidateQueryView(String productCode) {
        String detailKey = detailKey(productCode);
        RedisSafeExecutor.run(() -> redisTemplate.delete(detailKey));
        localHotInventoryCache.evict(productCode);
        scheduleDelayedDelete(detailKey, productCode);
    }

    private static String detailKey(String productCode) {
        return InventoryRedisKeys.product(productCode) + ":detail";
    }

    private void scheduleDelayedDelete(String detailKey, String productCode) {
        Thread.ofVirtual().name("inv-cache-delayed-" + productCode).start(() -> {
            try {
                Thread.sleep(DELAY.toMillis());
                RedisSafeExecutor.run(() -> redisTemplate.delete(detailKey));
                localHotInventoryCache.evict(productCode);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.debug("Delayed cache delete interrupted for {}", productCode);
            }
        });
    }
}
