package com.vincent.inventoryservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InventoryMutationRequest(
        @NotBlank String productCode,
        @NotNull @Min(1) Integer quantity,
        @NotBlank String requestId
) {
}
