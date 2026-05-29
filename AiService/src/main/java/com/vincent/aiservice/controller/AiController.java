package com.vincent.aiservice.controller;

import com.vincent.aiservice.dto.ApiResponse;
import com.vincent.aiservice.dto.ChatRequest;
import com.vincent.aiservice.dto.ChatResponse;
import com.vincent.aiservice.dto.HealthResponse;
import com.vincent.aiservice.dto.RecommendationResponse;
import com.vincent.aiservice.service.AiAssistantService;
import com.vincent.aiservice.service.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final RecommendationService recommendationService;
    private final AiAssistantService aiAssistantService;

    public AiController(
            RecommendationService recommendationService,
            AiAssistantService aiAssistantService
    ) {
        this.recommendationService = recommendationService;
        this.aiAssistantService = aiAssistantService;
    }

    @GetMapping("/health")
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.ok(new HealthResponse("UP"));
    }

    @GetMapping("/recommendations")
    public ApiResponse<RecommendationResponse> recommend(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String context
    ) {
        // TODO: resolve username from jwt.getSubject() and delegate to recommendationService.recommend
        return ApiResponse.ok(null);
    }

    @GetMapping("/recommendations/{productCode}")
    public ApiResponse<RecommendationResponse> recommendForProduct(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String productCode
    ) {
        // TODO: resolve username from jwt and delegate to recommendationService.recommendForProduct
        return ApiResponse.ok(null);
    }

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChatRequest request
    ) {
        return ApiResponse.ok(aiAssistantService.chat(jwt.getSubject(), request));
    }
}
