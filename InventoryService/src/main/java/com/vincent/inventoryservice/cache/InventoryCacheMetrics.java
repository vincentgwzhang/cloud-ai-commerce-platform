package com.vincent.inventoryservice.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class InventoryCacheMetrics {

    private final Counter cacheHit;
    private final Counter cacheMiss;
    private final Counter idempotencyDuplicate;

    public InventoryCacheMetrics(MeterRegistry meterRegistry) {
        this.cacheHit = meterRegistry.counter("inventory_cache_hit_total");
        this.cacheMiss = meterRegistry.counter("inventory_cache_miss_total");
        this.idempotencyDuplicate = meterRegistry.counter("inventory_idempotency_duplicate_total");
    }

    public void recordHit() {
        cacheHit.increment();
    }

    public void recordMiss() {
        cacheMiss.increment();
    }

    public void recordIdempotencyDuplicate() {
        idempotencyDuplicate.increment();
    }
}
