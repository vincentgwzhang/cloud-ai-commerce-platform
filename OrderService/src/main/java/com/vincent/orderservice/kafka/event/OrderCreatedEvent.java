package com.vincent.orderservice.kafka.event;

import java.math.BigDecimal;

/**
 * Published after a new order is persisted (status CREATED).
 * Inventory-service (future) consumes this for Saga choreography step 1.
 */
public record OrderCreatedEvent(
        String orderNo,
        String productCode,
        int quantity,
        BigDecimal amount,
        String requestId
) {
}
