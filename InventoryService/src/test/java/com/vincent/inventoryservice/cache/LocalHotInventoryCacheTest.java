package com.vincent.inventoryservice.cache;

import com.vincent.inventoryservice.config.InventoryProperties;
import com.vincent.inventoryservice.dto.InventoryResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalHotInventoryCacheTest {

    @Test
    void storesReadsAndEvictsHotInventory() {
        LocalHotInventoryCache cache = new LocalHotInventoryCache(properties(List.of("P1", "P2")));
        InventoryResponse response = new InventoryResponse("P1", 10, 2, 1);

        cache.put("P1", response);

        assertThat(cache.get("P1")).contains(response);

        cache.evict("P1");

        assertThat(cache.get("P1")).isEmpty();
    }

    @Test
    void emptyHotProductListStillAllowsOneEntry() {
        LocalHotInventoryCache cache = new LocalHotInventoryCache(properties(List.of()));
        InventoryResponse response = new InventoryResponse("P1", 10, 2, 1);

        cache.put("P1", response);

        assertThat(cache.get("P1")).contains(response);
    }

    private static InventoryProperties properties(List<String> hotProductCodes) {
        return new InventoryProperties(
                Duration.ofMinutes(5),
                Duration.ofMinutes(5),
                Duration.ofSeconds(3),
                Duration.ofMinutes(1),
                0,
                hotProductCodes
        );
    }
}
