package com.vincent.aiservice.kafka.event;

import java.time.Instant;

/**
 * Consumed from {@code product-deleted} — published by product-service when a product is removed.
 */
public record ProductDeletedEvent(
        String eventId,
        String eventType,
        String productCode,
        long version,
        Instant occurredAt,
        String requestId,
        String traceId
) {
}
