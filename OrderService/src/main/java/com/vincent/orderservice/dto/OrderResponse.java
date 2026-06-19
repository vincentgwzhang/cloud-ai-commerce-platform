package com.vincent.orderservice.dto;

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
}
