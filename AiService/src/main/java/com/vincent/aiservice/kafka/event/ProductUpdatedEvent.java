package com.vincent.aiservice.kafka.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Consumed from {@code product-updated} — full snapshot published by product-service.
 */
public record ProductUpdatedEvent(
        String eventId,
        String eventType,
        String productCode,
        String name,
        String description,
        BigDecimal price,
        String status,
        long version,
        Instant occurredAt,
        String requestId,
        String traceId
) {
}
