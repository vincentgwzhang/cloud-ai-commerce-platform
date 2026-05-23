package com.vincent.authservice.dto;

public record TokenValidationResponse(
        boolean valid,
        String username,
        String role
) {
}
