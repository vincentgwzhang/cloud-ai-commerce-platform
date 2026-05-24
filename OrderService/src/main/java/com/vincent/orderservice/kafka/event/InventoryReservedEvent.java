package com.vincent.orderservice.kafka.event;

/**
 * Consumed when inventory reservation succeeds (published by inventory-service in a later iteration).
 */
public record InventoryReservedEvent(
        String orderNo,
        String productCode,
        int quantity
) {
}
