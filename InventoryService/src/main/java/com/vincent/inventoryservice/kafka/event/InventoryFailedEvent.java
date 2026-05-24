package com.vincent.inventoryservice.kafka.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when reservation cannot complete (e.g. insufficient stock).
 */
public record InventoryFailedEvent(
        String eventId,
        String eventType,
        String orderNo,
        String productCode,
        String reason,
        Instant timestamp
) {

    public static final String EVENT_TYPE = "INVENTORY_FAILED";

    public static InventoryFailedEvent of(String orderNo, String productCode, String reason) {
        return new InventoryFailedEvent(
                UUID.randomUUID().toString(),
                EVENT_TYPE,
                orderNo,
                productCode,
                reason,
                Instant.now()
        );
    }
}
