package com.vincent.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String issuer, String publicKeyPath, List<String> publicKeyFallbackPaths) {

    public JwtProperties {
        if (publicKeyFallbackPaths == null) {
            publicKeyFallbackPaths = List.of();
        }
    }
}
