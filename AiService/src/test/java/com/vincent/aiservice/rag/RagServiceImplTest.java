package com.vincent.aiservice.rag;

import com.vincent.aiservice.config.RagProperties;
import com.vincent.aiservice.exception.ExternalAiException;
import com.vincent.aiservice.rag.embedding.EmbeddingProvider;
import com.vincent.aiservice.rag.llm.LlmClient;
import com.vincent.aiservice.rag.vector.ProductMatch;
import com.vincent.aiservice.rag.vector.ProductVectorStore;
import com.vincent.aiservice.service.AiMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagServiceImplTest {

    private final EmbeddingProvider embeddingProvider = mock(EmbeddingProvider.class);
    private final ProductVectorStore vectorStore = mock(ProductVectorStore.class);
    private final LlmClient llmClient = mock(LlmClient.class);
    private final AiMetrics metrics = new AiMetrics(new SimpleMeterRegistry());
    private final RagServiceImpl service = new RagServiceImpl(
            embeddingProvider,
            vectorStore,
            llmClient,
            new RagPromptBuilder(),
            new RagProperties(2, "products", Duration.ofHours(1)),
            metrics
    );

    @Test
    void askEmbedsSearchesBuildsPromptAndReturnsSources() {
        float[] embedding = new float[]{0.1f, 0.2f};
        when(embeddingProvider.embed("camera phone")).thenReturn(embedding);
        when(vectorStore.similaritySearch(embedding, 2)).thenReturn(List.of(
                new ProductMatch("PHONE-1", 0.12, "Camera phone", Map.of()),
                new ProductMatch("PHONE-2", 0.34, "Budget phone", Map.of())
        ));
        when(llmClient.complete(any(), any())).thenReturn("Pick PHONE-1.");
        when(llmClient.model()).thenReturn("gpt-test");

        var response = service.ask("vincent", "camera phone");

        assertThat(response.answer()).isEqualTo("Pick PHONE-1.");
        assertThat(response.model()).isEqualTo("gpt-test");
        assertThat(response.sources()).extracting("productCode").containsExactly("PHONE-1", "PHONE-2");
        verify(vectorStore).similaritySearch(embedding, 2);
        verify(llmClient).complete(
                org.mockito.ArgumentMatchers.contains("shopping assistant"),
                org.mockito.ArgumentMatchers.contains("[PHONE-1] Camera phone")
        );
    }

    @Test
    void askRecordsFailureAndRethrowsRuntimeException() {
        when(embeddingProvider.embed("boom")).thenThrow(new ExternalAiException("embedding down"));

        assertThatThrownBy(() -> service.ask("vincent", "boom"))
                .isInstanceOf(ExternalAiException.class)
                .hasMessageContaining("embedding down");

        verify(embeddingProvider).embed(eq("boom"));
    }
}
