package com.vincent.aiservice.service;

import com.vincent.aiservice.dto.ConversationResponse;

public interface ConversationService {

    ConversationResponse getConversation(String username, String conversationId);

    void deleteConversation(String username, String conversationId);
}
