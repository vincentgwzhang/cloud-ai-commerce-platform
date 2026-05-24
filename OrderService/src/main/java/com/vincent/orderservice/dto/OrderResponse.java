package com.vincent.orderservice.dto;

import com.vincent.orderservice.entity.Order;
import com.vincent.orderservice.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        String orderNo,
        String productCode,
        int quantity,
        BigDecimal amount,
        OrderStatus status,
        String requestId,
        Instant createdAt,
        Instant updatedAt
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getOrderNo(),
                order.getProductCode(),
                order.getQuantity(),
                order.getAmount(),
                order.getStatus(),
                order.getRequestId(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
