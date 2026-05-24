package com.vincent.inventoryservice.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class InventoryMetrics {

    private final Counter reservationSuccess;
    private final Counter reservationFailure;
    private final Counter orderCreatedEventConsumed;
    private final Counter inventoryEventPublished;
    private final Counter kafkaConsumeFailure;

    public InventoryMetrics(MeterRegistry meterRegistry) {
        this.reservationSuccess = meterRegistry.counter("inventory_reservation_success_total");
        this.reservationFailure = meterRegistry.counter("inventory_reservation_failure_total");
        this.orderCreatedEventConsumed = meterRegistry.counter("inventory_order_created_consumed_total");
        this.inventoryEventPublished = meterRegistry.counter("inventory_event_published_total");
        this.kafkaConsumeFailure = meterRegistry.counter("inventory_kafka_consume_failure_total");
    }

    public void recordReservationSuccess() {
        reservationSuccess.increment();
    }

    public void recordReservationFailure() {
        reservationFailure.increment();
    }

    public void recordOrderCreatedEventConsumed() {
        orderCreatedEventConsumed.increment();
    }

    public void recordInventoryEventPublished() {
        inventoryEventPublished.increment();
    }

    public void recordKafkaConsumeFailure() {
        kafkaConsumeFailure.increment();
    }
}
