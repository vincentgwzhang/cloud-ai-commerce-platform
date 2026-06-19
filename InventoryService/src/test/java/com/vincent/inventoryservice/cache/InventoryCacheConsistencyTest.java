package com.vincent.inventoryservice.cache;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class InventoryCacheConsistencyTest {

    @Test
    void invalidateQueryViewDeletesRedisAndLocalCacheTwice() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        LocalHotInventoryCache localHotInventoryCache = mock(LocalHotInventoryCache.class);
        InventoryCacheConsistency consistency = new InventoryCacheConsistency(redisTemplate, localHotInventoryCache);

        consistency.invalidateQueryView("P1");

        verify(redisTemplate, timeout(1000).times(2)).delete("inventory:product:P1:detail");
        verify(localHotInventoryCache, timeout(1000).times(2)).evict("P1");
    }
}
