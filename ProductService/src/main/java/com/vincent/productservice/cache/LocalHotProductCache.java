package com.vincent.productservice.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.vincent.productservice.config.ProductCacheProperties;
import com.vincent.productservice.dto.ProductResponse;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * L1 in-process cache for configured hot product IDs — reduces QPS on Redis hot keys.
 *
 * <p>Even with Redis, a single hot key can saturate network/CPU; a short local TTL absorbs bursts.
 *
 * <p>这里使用 Guava Cache，而不是自己维护 ConcurrentHashMap + expiresAt：
 * 1. Cache 本身是线程安全的，适合作为本机内存缓存使用。
 * 2. expireAfterWrite 会自动处理“写入多久后过期”，get 时不需要手动比较时间。
 * 3. maximumSize 可以防止本机缓存无限增长；这里按配置的 hotProductIds 数量限制容量。
 */
@Component
public class LocalHotProductCache {

    private final Cache<Long, ProductResponse> store;

    public LocalHotProductCache(ProductCacheProperties properties) {
        this.store = CacheBuilder.newBuilder()
                .expireAfterWrite(properties.localCacheTtl().toNanos(), TimeUnit.NANOSECONDS)
                .maximumSize(resolveMaximumSize(properties))
                .build();
    }

    public Optional<ProductResponse> get(Long id) {
        return Optional.ofNullable(store.getIfPresent(id));
    }

    public void put(Long id, ProductResponse response) {
        store.put(id, response);
    }

    public void evict(Long id) {
        store.invalidate(id);
    }

    private static long resolveMaximumSize(ProductCacheProperties properties) {
        var hotProductIds = properties.hotProductIds();
        if (hotProductIds == null || hotProductIds.isEmpty()) {
            return 1L;
        }
        return hotProductIds.size();
    }
}
