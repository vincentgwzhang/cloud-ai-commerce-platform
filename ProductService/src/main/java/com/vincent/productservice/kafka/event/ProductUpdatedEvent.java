package com.vincent.productservice.kafka.event;

import java.math.BigDecimal;
import java.time.Instant;

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
