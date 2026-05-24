package com.vincent.gatewayservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, GatewayRouteProperties.class})
public class ApplicationConfig {
}
