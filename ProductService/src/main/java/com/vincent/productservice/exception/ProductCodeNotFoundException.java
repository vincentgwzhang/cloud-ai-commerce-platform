package com.vincent.productservice.exception;

public class ProductCodeNotFoundException extends RuntimeException {

    public ProductCodeNotFoundException(String productCode) {
        super("Product not found: " + productCode);
    }
}
