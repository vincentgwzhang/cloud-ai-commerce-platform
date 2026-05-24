package com.vincent.gatewayservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.routes")
public record GatewayRouteProperties(
        String productUri,
        String inventoryUri,
        String orderUri
) {
}
