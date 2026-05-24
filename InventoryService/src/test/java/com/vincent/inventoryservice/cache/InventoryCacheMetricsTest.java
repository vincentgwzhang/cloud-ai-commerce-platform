package com.vincent.inventoryservice.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryCacheMetricsTest {

    @Test
    void incrementsAllCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        InventoryCacheMetrics metrics = new InventoryCacheMetrics(registry);
        metrics.recordHit();
        metrics.recordMiss();
        metrics.recordIdempotencyDuplicate();
        assertThat(registry.get("inventory_cache_hit_total").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("inventory_idempotency_duplicate_total").counter().count()).isEqualTo(1.0);
    }
}
