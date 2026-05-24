package com.vincent.inventoryservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka")
public record InventoryKafkaProperties(
        Topics topics,
        int maxRetries,
        long retryIntervalMs
) {

    public record Topics(
            String orderCreated,
            String inventoryReserved,
            String inventoryFailed,
            String inventoryDlq
    ) {
    }
}
