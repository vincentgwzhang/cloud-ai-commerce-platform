package com.vincent.authservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void authOpenApiDefinesBearerSecurity() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI openAPI = config.authOpenApi();

        assertThat(openAPI.getInfo().getTitle()).isEqualTo("Auth Service API");
        assertThat(openAPI.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
        assertThat(openAPI.getSecurity()).isNotEmpty();
    }
}
