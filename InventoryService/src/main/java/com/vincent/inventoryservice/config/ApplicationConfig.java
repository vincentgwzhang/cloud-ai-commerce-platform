package com.vincent.inventoryservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({InventoryProperties.class, JwtProperties.class})
public class ApplicationConfig {
}
