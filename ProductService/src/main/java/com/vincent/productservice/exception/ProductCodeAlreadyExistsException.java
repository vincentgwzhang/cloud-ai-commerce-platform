package com.vincent.productservice.exception;

public class ProductCodeAlreadyExistsException extends RuntimeException {

    public ProductCodeAlreadyExistsException(String productCode) {
        super("Product already exists: " + productCode);
    }
}
