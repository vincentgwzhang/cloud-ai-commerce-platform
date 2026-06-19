package com.vincent.productservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, ProductCacheProperties.class, ProductKafkaProperties.class})
public class ApplicationConfig {
}
