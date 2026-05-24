package com.vincent.inventoryservice.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class InventoryMetrics {

    private final Counter reservationSuccess;
    private final Counter reservationFailure;

    public InventoryMetrics(MeterRegistry meterRegistry) {
        this.reservationSuccess = meterRegistry.counter("inventory_reservation_success");
        this.reservationFailure = meterRegistry.counter("inventory_reservation_failure");
    }

    public void recordReservationSuccess() {
        reservationSuccess.increment();
    }

    public void recordReservationFailure() {
        reservationFailure.increment();
    }
}
