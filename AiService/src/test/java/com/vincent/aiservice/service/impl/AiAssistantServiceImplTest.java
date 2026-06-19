package com.vincent.aiservice.service.impl;

import com.vincent.aiservice.ai.AiProvider;
import com.vincent.aiservice.dto.ChatRequest;
import com.vincent.aiservice.memory.ConversationMemoryRepository;
import com.vincent.aiservice.memory.ConversationMessage;
import com.vincent.aiservice.service.AiMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAssistantServiceImplTest {

    private final AiProvider aiProvider = mock(AiProvider.class);
    private final ConversationMemoryRepository memoryRepository = mock(ConversationMemoryRepository.class);
    private final ObjectProvider<Tracer> tracerProvider = mock();
    private final AiAssistantServiceImpl service = new AiAssistantServiceImpl(
            aiProvider,
            memoryRepository,
            new AiMetrics(new SimpleMeterRegistry()),
            tracerProvider
    );

    @Test
    void chatGeneratesConversationIdPersistsMessagesAndReturnsProviderName() {
        when(tracerProvider.getIfAvailable()).thenReturn(null);
        when(aiProvider.chat("u1", "hello")).thenReturn("hi");
        when(aiProvider.name()).thenReturn("stub");

        var response = service.chat("u1", new ChatRequest("hello", null));

        assertThat(response.answer()).isEqualTo("hi");
        assertThat(response.source()).isEqualTo("stub");
        assertThat(response.conversationId()).isNotBlank();
        verify(memoryRepository, times(2)).append(eq("u1"), eq(response.conversationId()), any(ConversationMessage.class));
    }

    @Test
    void chatUsesProvidedConversationIdAndContinuesWhenMemoryFails() {
        when(tracerProvider.getIfAvailable()).thenReturn(null);
        doThrow(new IllegalStateException("redis down"))
                .when(memoryRepository).append(eq("u1"), eq("c1"), any(ConversationMessage.class));
        when(aiProvider.chat("u1", "hello")).thenReturn("hi");
        when(aiProvider.name()).thenReturn("stub");

        var response = service.chat("u1", new ChatRequest("hello", "c1"));

        assertThat(response.conversationId()).isEqualTo("c1");
        assertThat(response.answer()).isEqualTo("hi");
    }

    @Test
    void chatRecordsFailureAndRethrowsProviderErrors() {
        when(tracerProvider.getIfAvailable()).thenReturn(null);
        when(aiProvider.chat("u1", "boom")).thenThrow(new IllegalStateException("provider down"));
        when(aiProvider.name()).thenReturn("stub");

        assertThatThrownBy(() -> service.chat("u1", new ChatRequest("boom", "c1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provider down");
    }
}
