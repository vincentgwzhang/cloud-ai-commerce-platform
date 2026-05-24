package com.vincent.orderservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.order.cache")
public record OrderCacheProperties(
        Duration idempotencyTtl,
        Duration orderCacheTtl,
        int ttlJitterMaxSeconds
) {
}
