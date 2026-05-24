package com.vincent.gatewayservice.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API gateway routes — single entry for product, inventory, and order services.
 *
 * <p>Clients talk to port 8088; the gateway forwards to backend service URLs configured
 * per profile (localhost ports locally, K8s ClusterIP service names in Minikube).
 */
@Configuration
public class GatewayRouteConfig {

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder, GatewayRouteProperties routes) {
        return builder.routes()
                .route("product-service", r -> r
                        .path("/api/v1/products/**")
                        .uri(routes.productUri()))
                .route("inventory-service", r -> r
                        .path("/api/inventory/**")
                        .uri(routes.inventoryUri()))
                .route("order-service", r -> r
                        .path("/api/orders/**")
                        .uri(routes.orderUri()))
                .build();
    }
}
