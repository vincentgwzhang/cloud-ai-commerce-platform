package com.vincent.productservice.dto;

import com.vincent.productservice.entity.Product;
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

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getProductCode(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
