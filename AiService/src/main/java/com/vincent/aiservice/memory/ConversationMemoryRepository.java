package com.vincent.aiservice.memory;

import java.util.List;

/**
 * Port for conversation history persistence.
 *
 * <p>Clean-architecture boundary: the application layer depends on this abstraction, not on Redis.
 * The storage technology can be swapped (Redis today, DB/other tomorrow) without touching callers.
 *
 * <p>Ownership is enforced by scoping every operation to {@code userId}, so one user can never
 * read or delete another user's conversation.
 */
public interface ConversationMemoryRepository {

    /** Append a message to the conversation and refresh its expiration. */
    void append(String userId, String conversationId, ConversationMessage message);

    /** Load the full message history (oldest first); empty when the conversation does not exist. */
    List<ConversationMessage> findMessages(String userId, String conversationId);

    /** Whether the conversation currently exists for this user. */
    boolean exists(String userId, String conversationId);

    /** Remove the conversation entirely. */
    void delete(String userId, String conversationId);
}
