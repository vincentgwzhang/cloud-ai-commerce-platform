package com.vincent.aiservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({
        AiProperties.class,
        JwtProperties.class,
        AiKafkaProperties.class,
        ConversationMemoryProperties.class,
        OpenAiProperties.class,
        RagProperties.class,
        ChromaProperties.class
})
public class ApplicationConfig {

    /**
     * Shared {@link RestClient.Builder} for the OpenAI / Chroma clients. Each client calls
     * {@code clone()} before customizing, so sharing a single builder is safe.
     */
    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
