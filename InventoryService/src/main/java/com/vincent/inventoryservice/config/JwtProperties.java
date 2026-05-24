package com.vincent.inventoryservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String issuer,
        String publicKeyPath
) {
}
