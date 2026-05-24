package com.vincent.inventoryservice.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryMetricsTest {

    @Test
    void incrementsCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        InventoryMetrics metrics = new InventoryMetrics(registry);

        metrics.recordReservationSuccess();
        metrics.recordReservationFailure();

        assertThat(registry.get("inventory_reservation_success").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("inventory_reservation_failure").counter().count()).isEqualTo(1.0);
    }
}
