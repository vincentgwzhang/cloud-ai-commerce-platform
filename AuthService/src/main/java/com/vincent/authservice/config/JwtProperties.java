package com.vincent.authservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String issuer,
        long expirationSeconds,
        long refreshExpirationSeconds,
        String privateKeyPath,
        String publicKeyPath
) {
}
