package com.vincent.productservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app.cache.product")
public record ProductCacheProperties(
        Duration detailTtl,
        Duration hotTtl,
        Duration nullCacheTtl,
        Duration lockTtl,
        Duration localCacheTtl,
        int ttlJitterMaxSeconds,
        List<Long> hotProductIds
) {
}
