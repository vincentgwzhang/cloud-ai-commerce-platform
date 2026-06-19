package com.vincent.productservice.kafka.event;

import java.time.Instant;

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
