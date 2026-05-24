package com.vincent.inventoryservice.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class InventoryLockMetrics {

    private final Counter lockAcquired;
    private final Counter lockFailed;

    public InventoryLockMetrics(MeterRegistry meterRegistry) {
        this.lockAcquired = meterRegistry.counter("inventory_lock_acquired_total");
        this.lockFailed = meterRegistry.counter("inventory_lock_failed_total");
    }

    public void recordAcquired() {
        lockAcquired.increment();
    }

    public void recordFailed() {
        lockFailed.increment();
    }
}
