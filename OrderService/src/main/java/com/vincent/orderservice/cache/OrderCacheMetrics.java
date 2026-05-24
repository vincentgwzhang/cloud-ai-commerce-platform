package com.vincent.orderservice.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OrderCacheMetrics {

    private final Counter idempotencyDuplicate;

    public OrderCacheMetrics(MeterRegistry meterRegistry) {
        this.idempotencyDuplicate = meterRegistry.counter("idempotency_duplicate_total");
    }

    public void recordIdempotencyDuplicate() {
        idempotencyDuplicate.increment();
    }
}
