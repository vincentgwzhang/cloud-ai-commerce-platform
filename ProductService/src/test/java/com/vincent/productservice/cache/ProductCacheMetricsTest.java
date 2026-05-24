package com.vincent.productservice.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCacheMetricsTest {

    @Test
    void incrementsHitAndMiss() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProductCacheMetrics metrics = new ProductCacheMetrics(registry);
        metrics.recordHit();
        metrics.recordMiss();
        assertThat(registry.get("cache_hit_total").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("cache_miss_total").counter().count()).isEqualTo(1.0);
    }
}
