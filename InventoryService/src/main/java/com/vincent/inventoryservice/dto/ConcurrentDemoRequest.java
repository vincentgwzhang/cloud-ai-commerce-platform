package com.vincent.inventoryservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ConcurrentDemoRequest(
        @NotBlank String productCode,
        @Min(1) @Max(500) int concurrentRequests,
        @Min(1) @Max(10) int quantityPerRequest
) {
}
