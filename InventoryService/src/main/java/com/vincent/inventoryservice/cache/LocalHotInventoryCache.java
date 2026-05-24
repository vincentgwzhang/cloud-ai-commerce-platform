package com.vincent.inventoryservice.cache;

import com.vincent.inventoryservice.dto.InventoryResponse;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** L1 cache for high-traffic SKUs — shields Redis hot keys. */
@Component
public class LocalHotInventoryCache {

    private static final class Entry {
        final InventoryResponse value;
        final Instant expiresAt;

        Entry(InventoryResponse value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        boolean alive() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final Duration localTtl;

    public LocalHotInventoryCache(com.vincent.inventoryservice.config.InventoryProperties properties) {
        this.localTtl = properties.localCacheTtl();
    }

    public Optional<InventoryResponse> get(String productCode) {
        Entry entry = store.get(productCode);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.alive()) {
            store.remove(productCode);
            return Optional.empty();
        }
        return Optional.of(entry.value);
    }

    public void put(String productCode, InventoryResponse response) {
        store.put(productCode, new Entry(response, Instant.now().plus(localTtl)));
    }

    public void evict(String productCode) {
        store.remove(productCode);
    }
}
