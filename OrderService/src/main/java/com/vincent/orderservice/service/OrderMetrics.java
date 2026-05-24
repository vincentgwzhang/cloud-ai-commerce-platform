package com.vincent.orderservice.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics {

    private final Counter orderCreated;
    private final Counter orderFailed;
    private final Counter kafkaConsumeFailure;

    public OrderMetrics(MeterRegistry meterRegistry) {
        this.orderCreated = meterRegistry.counter("order_created_total");
        this.orderFailed = meterRegistry.counter("order_failed_total");
        this.kafkaConsumeFailure = meterRegistry.counter("kafka_consume_failure_total");
    }

    public void recordOrderCreated() {
        orderCreated.increment();
    }

    public void recordOrderFailed() {
        orderFailed.increment();
    }

    public void recordKafkaConsumeFailure() {
        kafkaConsumeFailure.increment();
    }
}
