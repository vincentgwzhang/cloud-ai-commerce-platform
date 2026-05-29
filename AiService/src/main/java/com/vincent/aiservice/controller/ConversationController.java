package com.vincent.aiservice.controller;

import com.vincent.aiservice.dto.ApiResponse;
import com.vincent.aiservice.dto.ConversationResponse;
import com.vincent.aiservice.service.ConversationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/conversation")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationResponse> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String conversationId
    ) {
        return ApiResponse.ok(conversationService.getConversation(jwt.getSubject(), conversationId));
    }

    @DeleteMapping("/{conversationId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String conversationId
    ) {
        conversationService.deleteConversation(jwt.getSubject(), conversationId);
        return ApiResponse.ok("deleted", null);
    }
}
