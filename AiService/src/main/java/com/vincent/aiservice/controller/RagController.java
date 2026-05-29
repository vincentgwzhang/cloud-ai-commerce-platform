package com.vincent.aiservice.controller;

import com.vincent.aiservice.dto.ApiResponse;
import com.vincent.aiservice.dto.AskRequest;
import com.vincent.aiservice.dto.AskResponse;
import com.vincent.aiservice.rag.RagService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/ask")
    public ApiResponse<AskResponse> ask(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AskRequest request
    ) {
        return ApiResponse.ok(ragService.ask(jwt.getSubject(), request.question()));
    }
}
