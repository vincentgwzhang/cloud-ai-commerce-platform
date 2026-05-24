package com.vincent.orderservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({OrderKafkaProperties.class, OrderCacheProperties.class, JwtProperties.class})
public class ApplicationConfig {
}
