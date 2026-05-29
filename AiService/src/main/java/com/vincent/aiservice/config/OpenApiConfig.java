package com.vincent.aiservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI aiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Service API")
                        .description("AI-driven product recommendations and shopping assistant")
                        .version("1.0.0"));
    }
}
