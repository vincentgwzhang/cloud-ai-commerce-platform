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
        metrics.recordOrderCreatedEventConsumed();
        metrics.recordInventoryEventPublished();
        metrics.recordKafkaConsumeFailure();

        assertThat(registry.get("inventory_reservation_success_total").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("inventory_reservation_failure_total").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("inventory_order_created_consumed_total").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("inventory_event_published_total").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("inventory_kafka_consume_failure_total").counter().count()).isEqualTo(1.0);
    }
}
