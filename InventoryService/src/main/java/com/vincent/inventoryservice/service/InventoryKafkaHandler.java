package com.vincent.inventoryservice.service;

import com.vincent.inventoryservice.exception.InsufficientInventoryException;
import com.vincent.inventoryservice.kafka.InventoryEventPublisher;
import com.vincent.inventoryservice.observability.BusinessEventLog;
import com.vincent.inventoryservice.kafka.event.InventoryFailedEvent;
import com.vincent.inventoryservice.kafka.event.InventoryReservedEvent;
import com.vincent.inventoryservice.kafka.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Bridges Kafka events to existing {@link InventoryService#reserve} — no rewrite of core logic.
 */
@Service
public class InventoryKafkaHandler {

    private static final Logger log = LoggerFactory.getLogger(InventoryKafkaHandler.class);

    private final InventoryService inventoryService;
    private final InventoryEventPublisher eventPublisher;

    public InventoryKafkaHandler(InventoryService inventoryService, InventoryEventPublisher eventPublisher) {
        this.inventoryService = inventoryService;
        this.eventPublisher = eventPublisher;
    }

    public void handleOrderCreated(OrderCreatedEvent event) {
        String idempotencyKey = resolveIdempotencyKey(event);
        try {
            inventoryService.reserve(event.productCode(), event.quantity(), idempotencyKey);
            eventPublisher.publishReserved(
                    InventoryReservedEvent.of(event.orderNo(), event.productCode(), event.username(), event.quantity())
            );
            BusinessEventLog.info(log, "INVENTORY_RESERVED", event.orderNo(), event.productCode(), event.eventId());
        } catch (InsufficientInventoryException ex) {
            eventPublisher.publishFailed(
                    InventoryFailedEvent.of(event.orderNo(), event.productCode(), ex.getMessage())
            );
            BusinessEventLog.info(log, "INVENTORY_FAILED", event.orderNo(), event.productCode(), event.eventId());
            log.warn("Reservation failed for order {}: {}", event.orderNo(), ex.getMessage());
        }
    }

    /**
     * Reuse HTTP idempotency key when present; otherwise derive a stable key per order.
     */
    private static String resolveIdempotencyKey(OrderCreatedEvent event) {
        if (StringUtils.hasText(event.requestId())) {
            return event.requestId();
        }
        return "kafka-order:" + event.orderNo();
    }
}
