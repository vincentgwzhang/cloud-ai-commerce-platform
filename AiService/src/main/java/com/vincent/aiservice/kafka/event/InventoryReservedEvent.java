package com.vincent.aiservice.kafka.event;

import java.time.Instant;

/**
 * Consumed from {@code inventory-reserved} — mirrors the payload published by inventory-service.
 */
public record InventoryReservedEvent(
        String eventId,
        String eventType,
        String orderNo,
        String productCode,
        int quantity,
        Instant timestamp
) {
}
