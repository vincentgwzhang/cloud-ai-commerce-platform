package com.vincent.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ChromaDB connection settings. Chroma runs as an external host dependency (like MySQL/Redis/Kafka).
 *
 * @param baseUrl  Chroma server base URL
 * @param tenant   Chroma v2 tenant
 * @param database Chroma v2 database
 */
@ConfigurationProperties(prefix = "app.ai.chroma")
public record ChromaProperties(
        String baseUrl,
        String tenant,
        String database
) {
}
