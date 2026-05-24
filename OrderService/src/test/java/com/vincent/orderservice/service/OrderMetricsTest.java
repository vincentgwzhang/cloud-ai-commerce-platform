package com.vincent.orderservice.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMetricsTest {

    @Test
    void incrementsCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OrderMetrics metrics = new OrderMetrics(registry);

        metrics.recordOrderCreated();
        metrics.recordOrderFailed();
        metrics.recordKafkaConsumeFailure();

        assertThat(registry.get("order_created_total").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("order_failed_total").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("kafka_consume_failure_total").counter().count()).isEqualTo(1.0);
    }
}
