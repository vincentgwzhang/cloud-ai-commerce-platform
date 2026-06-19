package com.vincent.aiservice.service.impl;

import com.vincent.aiservice.exception.ConversationNotFoundException;
import com.vincent.aiservice.memory.ConversationMemoryRepository;
import com.vincent.aiservice.memory.ConversationMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationServiceImplTest {

    private final ConversationMemoryRepository repository = mock(ConversationMemoryRepository.class);
    private final ConversationServiceImpl service = new ConversationServiceImpl(repository);

    @Test
    void getConversationReturnsStoredMessages() {
        when(repository.exists("u1", "c1")).thenReturn(true);
        when(repository.findMessages("u1", "c1")).thenReturn(List.of(ConversationMessage.user("hi")));

        var response = service.getConversation("u1", "c1");

        assertThat(response.conversationId()).isEqualTo("c1");
        assertThat(response.messages()).hasSize(1);
    }

    @Test
    void deleteConversationDeletesExistingConversation() {
        when(repository.exists("u1", "c1")).thenReturn(true);

        service.deleteConversation("u1", "c1");

        verify(repository).delete("u1", "c1");
    }

    @Test
    void operationsThrowWhenConversationDoesNotExist() {
        when(repository.exists("u1", "missing")).thenReturn(false);

        assertThatThrownBy(() -> service.getConversation("u1", "missing"))
                .isInstanceOf(ConversationNotFoundException.class);
        assertThatThrownBy(() -> service.deleteConversation("u1", "missing"))
                .isInstanceOf(ConversationNotFoundException.class);
    }
}
