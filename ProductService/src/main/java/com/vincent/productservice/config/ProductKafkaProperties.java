package com.vincent.productservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka")
public record ProductKafkaProperties(
        Topics topics
) {

    public record Topics(
            String productCreated,
            String productUpdated,
            String productDeleted
    ) {
    }
}
