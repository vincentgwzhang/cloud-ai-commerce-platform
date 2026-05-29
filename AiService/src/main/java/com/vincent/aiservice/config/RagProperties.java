package com.vincent.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Retrieval-augmented-generation tuning.
 *
 * @param topK     number of similar products retrieved per question
 * @param collection vector collection name
 * @param dedupTtl event de-duplication window (idempotent sync)
 */
@ConfigurationProperties(prefix = "app.ai.rag")
public record RagProperties(
        int topK,
        String collection,
        Duration dedupTtl
) {
}
