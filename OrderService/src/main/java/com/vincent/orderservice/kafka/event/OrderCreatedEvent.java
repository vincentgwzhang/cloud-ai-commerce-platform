package com.vincent.orderservice.kafka.event;

import com.vincent.orderservice.entity.Order;
import com.vincent.orderservice.observability.MdcSupport;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published after a new order is persisted (status CREATED).
 *
 * <p>Async communication decouples order-service from inventory-service: we do not call
 * inventory over HTTP here; inventory-service consumes this event and reserves stock later
 * (eventual consistency / Saga choreography).
 */
public record OrderCreatedEvent(
        String eventId,
        String eventType,
        String orderNo,
        String productCode,
        int quantity,
        BigDecimal amount,
        String requestId,
        String traceId,
        Instant timestamp
) {

    public static final String EVENT_TYPE = "ORDER_CREATED";

    public static OrderCreatedEvent from(Order order) {
        return new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                EVENT_TYPE,
                order.getOrderNo(),
                order.getProductCode(),
                order.getQuantity(),
                order.getAmount(),
                order.getRequestId(),
                MdcSupport.traceId().orElse(null),
                Instant.now()
        );
    }
}
