package com.vincent.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAI integration settings.
 *
 * @param apiKey         API key — leave blank in source; inject via {@code OPENAI_API_KEY}
 * @param baseUrl        API base URL (override for proxies / Azure-compatible gateways)
 * @param embeddingModel embedding model id
 * @param chatModel      chat completion model id
 */
@ConfigurationProperties(prefix = "app.ai.openai")
public record OpenAiProperties(
        String apiKey,
        String baseUrl,
        String embeddingModel,
        String chatModel
) {
}
