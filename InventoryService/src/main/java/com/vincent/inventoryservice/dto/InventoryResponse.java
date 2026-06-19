package com.vincent.inventoryservice.dto;

public record InventoryResponse(
        String productCode,
        int availableStock,
        int reservedStock,
        long version
) {
}
