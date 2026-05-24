package com.vincent.orderservice.exception;

public class DuplicateOrderRequestException extends RuntimeException {

    public DuplicateOrderRequestException(String requestId) {
        super("Duplicate order requestId: " + requestId);
    }
}
