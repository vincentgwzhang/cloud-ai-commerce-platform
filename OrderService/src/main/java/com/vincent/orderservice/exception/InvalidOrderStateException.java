package com.vincent.orderservice.exception;

import com.vincent.orderservice.entity.OrderStatus;

public class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(String orderNo, OrderStatus status, String action) {
        super("Cannot " + action + " order " + orderNo + " in status " + status);
    }
}
