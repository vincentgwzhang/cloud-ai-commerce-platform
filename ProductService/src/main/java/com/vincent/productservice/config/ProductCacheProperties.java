package com.vincent.productservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app.cache.product")
public record ProductCacheProperties(
        Duration ttl,
        Duration lockTtl,
        String keyPrefix,
        List<Long> hotProductIds
) {
}
