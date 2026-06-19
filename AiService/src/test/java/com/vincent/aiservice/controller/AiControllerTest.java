package com.vincent.aiservice.controller;

import com.vincent.aiservice.dto.ChatRequest;
import com.vincent.aiservice.dto.ChatResponse;
import com.vincent.aiservice.dto.RecommendationItem;
import com.vincent.aiservice.dto.RecommendationResponse;
import com.vincent.aiservice.service.AiAssistantService;
import com.vincent.aiservice.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiControllerTest {

    private final RecommendationService recommendationService = mock(RecommendationService.class);
    private final AiAssistantService aiAssistantService = mock(AiAssistantService.class);
    private final AiController controller = new AiController(recommendationService, aiAssistantService);

    @Test
    void healthReturnsUp() {
        assertThat(controller.health().data().status()).isEqualTo("UP");
    }

    @Test
    void recommendDelegatesWithJwtSubject() {
        RecommendationResponse response = response();
        when(recommendationService.recommend("u1", "ctx")).thenReturn(response);

        assertThat(controller.recommend(jwt(), "ctx").data()).isSameAs(response);
    }

    @Test
    void recommendForProductDelegatesWithJwtSubject() {
        RecommendationResponse response = response();
        when(recommendationService.recommendForProduct("u1", "P1")).thenReturn(response);

        assertThat(controller.recommendForProduct(jwt(), "P1").data()).isSameAs(response);
    }

    @Test
    void chatDelegatesWithJwtSubject() {
        ChatRequest request = new ChatRequest("hello", "c1");
        ChatResponse response = new ChatResponse("hi", "c1", "stub", "trace");
        when(aiAssistantService.chat("u1", request)).thenReturn(response);

        assertThat(controller.chat(jwt(), request).data()).isSameAs(response);
        verify(aiAssistantService).chat("u1", request);
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("token").header("alg", "none").subject("u1").build();
    }

    private static RecommendationResponse response() {
        return new RecommendationResponse(
                "u1",
                "generated",
                List.of(new RecommendationItem("P1", 0.9, "matched")),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}
