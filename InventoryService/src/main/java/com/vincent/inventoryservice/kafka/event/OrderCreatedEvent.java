package com.vincent.inventoryservice.kafka.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Consumed from {@code order-created} — mirrors the payload published by order-service.
 */
public record OrderCreatedEvent(
        String eventId,
        String eventType,
        String orderNo,
        String productCode,
        int quantity,
        BigDecimal amount,
        String requestId,
        String traceId,
        Instant timestamp
) {
}
