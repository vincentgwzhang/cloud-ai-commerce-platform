package com.vincent.inventoryservice.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vincent.inventoryservice.config.InventoryProperties;
import com.vincent.inventoryservice.dto.InventoryResponse;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * L1 cache for high-traffic SKUs — shields Redis hot keys.
 *
 * <p>Caffeine is a purpose-built local cache:
 * 1. It is thread-safe, so callers can use it from concurrent request threads.
 * 2. expireAfterWrite replaces manual expiresAt checks on every get.
 * 3. maximumSize avoids unbounded in-process memory growth.
 */
@Component
public class LocalHotInventoryCache {

    private final Cache<String, InventoryResponse> store;

    public LocalHotInventoryCache(InventoryProperties properties) {
        this.store = Caffeine.newBuilder()
                .expireAfterWrite(properties.localCacheTtl().toNanos(), TimeUnit.NANOSECONDS)
                .maximumSize(resolveMaximumSize(properties))
                .build();
    }

    public Optional<InventoryResponse> get(String productCode) {
        return Optional.ofNullable(store.getIfPresent(productCode));
    }

    public void put(String productCode, InventoryResponse response) {
        store.put(productCode, response);
    }

    public void evict(String productCode) {
        store.invalidate(productCode);
    }

    private static long resolveMaximumSize(InventoryProperties properties) {
        var hotProductCodes = properties.hotProductCodes();
        if (hotProductCodes == null || hotProductCodes.isEmpty()) {
            return 1L;
        }
        return hotProductCodes.size();
    }
}
