package com.vincent.inventoryservice.integration;

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

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.profiles.active=test")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class InventoryApiIntegrationTest {

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
    void getInventoryRequiresJwt() throws Exception {
        mockMvc.perform(get("/api/inventory/IPHONE17"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getInventoryWithJwtReturnsSeedData() throws Exception {
        mockMvc.perform(get("/api/inventory/IPHONE17").with(testJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productCode").value("IPHONE17"))
                .andExpect(jsonPath("$.data.availableStock").value(100));
    }

    @Test
    void deductAfterReserve() throws Exception {
        String reserveId = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productCode":"RTX5090","quantity":1,"requestId":"%s"}
                                """.formatted(reserveId))
                        .with(testJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reservedStock").value(1));

        mockMvc.perform(post("/api/inventory/deduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productCode":"RTX5090","quantity":1,"requestId":"%s"}
                                """.formatted(UUID.randomUUID()))
                        .with(testJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reservedStock").value(0));
    }

    @Test
    void reserveAndReleaseRoundTrip() throws Exception {
        String requestId = UUID.randomUUID().toString();
        String body = """
                {"productCode":"PS6","quantity":2,"requestId":"%s"}
                """.formatted(requestId);

        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(testJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableStock").value(48));

        String releaseBody = """
                {"productCode":"PS6","quantity":2,"requestId":"%s"}
                """.formatted(UUID.randomUUID());
        mockMvc.perform(post("/api/inventory/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(releaseBody)
                        .with(testJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableStock").value(50));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor testJwt() {
        return jwt().jwt(token -> token
                .issuer("auth-service-test")
                .subject("vincent")
                .claim("roles", java.util.List.of("USER")));
    }
}
