package com.vincent.orderservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka")
public record OrderKafkaProperties(
        Topics topics,
        int maxRetries,
        long retryIntervalMs
) {

    public record Topics(
            String orderCreated,
            String orderConfirmed,
            String orderFailed,
            String inventoryReserved,
            String inventoryFailed,
            String orderDlq
    ) {
    }
}
