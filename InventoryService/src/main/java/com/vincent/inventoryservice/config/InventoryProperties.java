package com.vincent.inventoryservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.inventory")
public record InventoryProperties(
        String cacheKeyPrefix,
        Duration cacheTtl,
        String idempotencyPrefix,
        Duration idempotencyTtl,
        String lockPrefix,
        Duration lockTtl
) {
}
