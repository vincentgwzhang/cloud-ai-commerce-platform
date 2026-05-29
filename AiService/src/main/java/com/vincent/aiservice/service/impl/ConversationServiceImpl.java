package com.vincent.aiservice.service.impl;

import com.vincent.aiservice.dto.ConversationResponse;
import com.vincent.aiservice.exception.ConversationNotFoundException;
import com.vincent.aiservice.memory.ConversationMemoryRepository;
import com.vincent.aiservice.service.ConversationService;
import org.springframework.stereotype.Service;

/**
 * Read/delete use cases for stored conversations.
 *
 * <p>Every call is scoped to the authenticated {@code username}, so a user can only access their
 * own conversations — the memory key embeds the user id, making cross-user access impossible.
 */
@Service
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMemoryRepository memoryRepository;

    public ConversationServiceImpl(ConversationMemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    @Override
    public ConversationResponse getConversation(String username, String conversationId) {
        if (!memoryRepository.exists(username, conversationId)) {
            throw new ConversationNotFoundException(conversationId);
        }
        return new ConversationResponse(conversationId, memoryRepository.findMessages(username, conversationId));
    }

    @Override
    public void deleteConversation(String username, String conversationId) {
        if (!memoryRepository.exists(username, conversationId)) {
            throw new ConversationNotFoundException(conversationId);
        }
        memoryRepository.delete(username, conversationId);
    }
}
