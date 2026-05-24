package com.vincent.inventoryservice.exception;

public class InsufficientInventoryException extends RuntimeException {

    public InsufficientInventoryException(String productCode, int requested, int available) {
        super("Insufficient inventory for %s: requested=%d, available=%d".formatted(productCode, requested, available));
    }
}
