package com.vincent.aiservice.dto;

public record ChatResponse(
        String answer,
        String conversationId,
        String source,
        String traceId
) {
}
