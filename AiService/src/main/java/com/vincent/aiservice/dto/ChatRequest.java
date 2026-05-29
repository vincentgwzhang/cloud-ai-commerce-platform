package com.vincent.aiservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Chat request. {@code conversationId} is optional — when blank, a new conversation is started.
 */
public record ChatRequest(
        @NotBlank @Size(max = 4000) String message,
        String conversationId
) {
}
