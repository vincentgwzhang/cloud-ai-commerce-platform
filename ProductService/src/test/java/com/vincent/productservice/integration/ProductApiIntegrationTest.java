package com.vincent.productservice.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.profiles.active=test")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ProductApiIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listProductsRequiresJwt() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listProductsWithJwtReturnsSeedData() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .with(jwt().jwt(token -> token
                                .issuer("auth-service-test")
                                .subject("vincent")
                                .claim("roles", java.util.List.of("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    void getProductByIdUsesCache() throws Exception {
        mockMvc.perform(get("/api/v1/products/1")
                        .with(jwt().jwt(token -> token
                                .issuer("auth-service-test")
                                .subject("vincent")
                                .claim("roles", java.util.List.of("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("iPhone 17"));

        mockMvc.perform(get("/api/v1/products/1")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(jwt().jwt(token -> token
                                .issuer("auth-service-test")
                                .subject("vincent")
                                .claim("roles", java.util.List.of("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productCode").value("IPHONE-17"));
    }

    @Test
    void hotProductsEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/products/hot")
                        .with(jwt().jwt(token -> token
                                .issuer("auth-service-test")
                                .subject("vincent")
                                .claim("roles", java.util.List.of("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }
}
