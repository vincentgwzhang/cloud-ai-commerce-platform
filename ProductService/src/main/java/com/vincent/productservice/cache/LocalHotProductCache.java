package com.vincent.productservice.cache;

import com.vincent.productservice.dto.ProductResponse;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L1 in-process cache for configured hot product IDs — reduces QPS on Redis hot keys.
 *
 * <p>Even with Redis, a single hot key can saturate network/CPU; a short local TTL absorbs bursts.
 */
@Component
public class LocalHotProductCache {

    private static final class Entry {
        final ProductResponse value;
        final Instant expiresAt;

        Entry(ProductResponse value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        boolean alive() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    private final Map<Long, Entry> store = new ConcurrentHashMap<>();
    private final Duration localTtl;

    public LocalHotProductCache(com.vincent.productservice.config.ProductCacheProperties properties) {
        this.localTtl = properties.localCacheTtl();
    }

    public Optional<ProductResponse> get(Long id) {
        Entry entry = store.get(id);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.alive()) {
            store.remove(id);
            return Optional.empty();
        }
        return Optional.of(entry.value);
    }

    public void put(Long id, ProductResponse response) {
        store.put(id, new Entry(response, Instant.now().plus(localTtl)));
    }

    public void evict(Long id) {
        store.remove(id);
    }
}
