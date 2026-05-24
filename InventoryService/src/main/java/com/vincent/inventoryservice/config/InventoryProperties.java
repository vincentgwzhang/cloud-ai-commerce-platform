package com.vincent.inventoryservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app.inventory")
public record InventoryProperties(
        Duration cacheTtl,
        Duration idempotencyTtl,
        Duration lockTtl,
        Duration localCacheTtl,
        int ttlJitterMaxSeconds,
        List<String> hotProductCodes
) {
}
