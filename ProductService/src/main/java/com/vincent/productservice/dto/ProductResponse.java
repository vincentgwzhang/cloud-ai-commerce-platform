package com.vincent.productservice.dto;

import com.vincent.productservice.entity.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String productCode,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        ProductStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
