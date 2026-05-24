package com.vincent.orderservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateOrderRequest(
        @NotBlank String productCode,
        @Min(1) int quantity,
        @NotBlank String requestId
) {
}
