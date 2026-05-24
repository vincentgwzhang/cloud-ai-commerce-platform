package com.vincent.inventoryservice.exception;

public class InventoryNotFoundException extends RuntimeException {

    public InventoryNotFoundException(String productCode) {
        super("Inventory not found for product: " + productCode);
    }
}
