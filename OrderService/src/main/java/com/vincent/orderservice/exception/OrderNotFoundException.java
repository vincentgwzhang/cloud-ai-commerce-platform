package com.vincent.orderservice.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String orderNo) {
        super("Order not found: " + orderNo);
    }
}
