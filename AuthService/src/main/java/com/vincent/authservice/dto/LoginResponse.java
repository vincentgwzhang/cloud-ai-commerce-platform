package com.vincent.authservice.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        long refreshExpiresIn,
        String username,
        String role
) {
}
