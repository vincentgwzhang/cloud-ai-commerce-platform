package com.vincent.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        Duration recommendationTtl,
        int ttlJitterMaxSeconds,
        int maxRecommendations,
        String provider
) {
}
