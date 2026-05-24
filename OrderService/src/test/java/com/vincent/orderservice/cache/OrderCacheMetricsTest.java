package com.vincent.orderservice.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderCacheMetricsTest {

    @Test
    void incrementsDuplicateCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OrderCacheMetrics metrics = new OrderCacheMetrics(registry);
        metrics.recordIdempotencyDuplicate();
        assertThat(registry.get("idempotency_duplicate_total").counter().count()).isEqualTo(1.0);
    }
}
