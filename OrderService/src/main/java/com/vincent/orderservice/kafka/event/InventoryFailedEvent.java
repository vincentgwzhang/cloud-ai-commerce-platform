package com.vincent.orderservice.kafka.event;

/**
 * Consumed when inventory reservation fails — order moves to FAILED (compensating path in full Saga).
 */
public record InventoryFailedEvent(
        String orderNo,
        String productCode,
        String reason
) {
}
