package com.vincent.inventoryservice.kafka.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when stock is reserved for an order (Saga step — inventory side).
 */
public record InventoryReservedEvent(
        String eventId,
        String eventType,
        String orderNo,
        String productCode,
        int quantity,
        Instant timestamp
) {

    public static final String EVENT_TYPE = "INVENTORY_RESERVED";

    public static InventoryReservedEvent of(String orderNo, String productCode, int quantity) {
        return new InventoryReservedEvent(
                UUID.randomUUID().toString(),
                EVENT_TYPE,
                orderNo,
                productCode,
                quantity,
                Instant.now()
        );
    }
}
