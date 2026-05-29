package com.vincent.aiservice.memory;

import java.time.Instant;

/** A single turn in a conversation (domain model, persisted in the memory store). */
public record ConversationMessage(
        MessageRole role,
        String content,
        Instant timestamp
) {

    public static ConversationMessage user(String content) {
        return new ConversationMessage(MessageRole.USER, content, Instant.now());
    }

    public static ConversationMessage assistant(String content) {
        return new ConversationMessage(MessageRole.ASSISTANT, content, Instant.now());
    }
}
