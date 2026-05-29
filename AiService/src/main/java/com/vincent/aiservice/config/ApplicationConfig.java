package com.vincent.aiservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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
}
