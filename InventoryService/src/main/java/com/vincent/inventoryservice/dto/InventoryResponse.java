package com.vincent.inventoryservice.dto;

import com.vincent.inventoryservice.entity.Inventory;

public record InventoryResponse(
        String productCode,
        int availableStock,
        int reservedStock,
        long version
) {

    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(
                inventory.getProductCode(),
                inventory.getAvailableStock(),
                inventory.getReservedStock(),
                inventory.getVersion()
        );
    }
}
