package com.vincent.orderservice.kafka.event;

/**
 * Consumed when inventory reservation succeeds (published by inventory-service).
 */
public record InventoryReservedEvent(
        String orderNo,
        String productCode,
        int quantity
) {
}
