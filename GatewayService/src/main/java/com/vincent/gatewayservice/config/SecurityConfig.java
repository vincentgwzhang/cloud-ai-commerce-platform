package com.vincent.gatewayservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Edge JWT validation — same issuer/keys as downstream resource servers.
 * Public health and demo paths match backend SecurityConfig permitAll rules.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.GET, "/api/gateway/health").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/products/health").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/inventory/health").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/orders/health").permitAll()
                        .pathMatchers("/api/inventory/demo/**", "/api/orders/demo/**").permitAll()
                        .pathMatchers("/actuator/health").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
