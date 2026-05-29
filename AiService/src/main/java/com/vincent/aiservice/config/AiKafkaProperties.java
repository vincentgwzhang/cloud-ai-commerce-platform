package com.vincent.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka")
public record AiKafkaProperties(
        Topics topics,
        int maxRetries,
        long retryIntervalMs
) {

    public record Topics(
            String orderCreated,
            String inventoryReserved,
            String productCreated,
            String productUpdated,
            String productDeleted,
            String aiDlq
    ) {
    }
}
