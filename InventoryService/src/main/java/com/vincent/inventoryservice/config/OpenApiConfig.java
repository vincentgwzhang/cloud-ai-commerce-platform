package com.vincent.inventoryservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI inventoryOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Inventory Service API")
                        .description("Inventory reservation, anti-overselling, Redis atomic demo")
                        .version("1.0.0"));
    }
}
