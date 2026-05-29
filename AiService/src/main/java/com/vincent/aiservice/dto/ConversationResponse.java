package com.vincent.aiservice.dto;

import com.vincent.aiservice.memory.ConversationMessage;

import java.util.List;

public record ConversationResponse(
        String conversationId,
        List<ConversationMessage> messages
) {
}
