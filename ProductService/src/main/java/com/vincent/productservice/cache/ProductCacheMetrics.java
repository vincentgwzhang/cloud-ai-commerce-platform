package com.vincent.productservice.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ProductCacheMetrics {

    private final Counter cacheHit;
    private final Counter cacheMiss;

    public ProductCacheMetrics(MeterRegistry meterRegistry) {
        this.cacheHit = meterRegistry.counter("cache_hit_total");
        this.cacheMiss = meterRegistry.counter("cache_miss_total");
    }

    public void recordHit() {
        cacheHit.increment();
    }

    public void recordMiss() {
        cacheMiss.increment();
    }
}
