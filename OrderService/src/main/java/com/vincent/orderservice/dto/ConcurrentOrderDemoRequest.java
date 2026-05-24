package com.vincent.orderservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ConcurrentOrderDemoRequest(
        @NotBlank String productCode,
        @Min(1) @Max(200) int concurrentRequests,
        @Min(1) @Max(5) int quantityPerRequest
) {
}
