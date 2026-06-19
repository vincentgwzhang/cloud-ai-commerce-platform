package com.vincent.aiservice.rag.sync;

import com.vincent.aiservice.config.RagProperties;
import com.vincent.aiservice.kafka.event.ProductCreatedEvent;
import com.vincent.aiservice.kafka.event.ProductDeletedEvent;
import com.vincent.aiservice.kafka.event.ProductUpdatedEvent;
import com.vincent.aiservice.rag.embedding.EmbeddingProvider;
import com.vincent.aiservice.rag.vector.ProductVector;
import com.vincent.aiservice.rag.vector.ProductVectorStore;
import com.vincent.aiservice.service.AiMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductVectorSyncServiceImplTest {

    private final EmbeddingProvider embeddingProvider = mock(EmbeddingProvider.class);
    private final ProductVectorStore vectorStore = mock(ProductVectorStore.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final ProductVectorSyncServiceImpl service = new ProductVectorSyncServiceImpl(
            embeddingProvider,
            vectorStore,
            redisTemplate,
            new RagProperties(3, "products", Duration.ofMinutes(5)),
            new AiMetrics(new SimpleMeterRegistry())
    );

    @Test
    void createdEmbedsCanonicalDocumentAndUpsertsVector() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent("ai:dedup:e1", "1", Duration.ofMinutes(5))).thenReturn(true);
        when(embeddingProvider.embed(any(String.class))).thenReturn(new float[]{0.1f, 0.2f});
        when(embeddingProvider.model()).thenReturn("embedding-model");

        service.onCreated(new ProductCreatedEvent(
                "e1", "PRODUCT_CREATED", "P1", "Camera phone", "Great camera",
                new BigDecimal("699.00"), "ACTIVE", 1, Instant.now(), "r1", "t1"
        ));

        verify(vectorStore).upsert(org.mockito.ArgumentMatchers.argThat(vector ->
                vector.productCode().equals("P1")
                        && vector.document().contains("Camera phone")
                        && vector.metadata().get("price").equals("699.00")
        ));
    }

    @Test
    void updatedSkipsDuplicateEvent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("ai:dedup:e2"), eq("1"), any(Duration.class))).thenReturn(false);

        service.onUpdated(new ProductUpdatedEvent(
                "e2", "PRODUCT_UPDATED", "P1", "Phone", "Desc",
                BigDecimal.TEN, "ACTIVE", 2, Instant.now(), "r1", "t1"
        ));

        verify(embeddingProvider, never()).embed(any(String.class));
        verify(vectorStore, never()).upsert(any(ProductVector.class));
    }

    @Test
    void deletedRemovesVectorWhenFirstSeen() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent("ai:dedup:e3", "1", Duration.ofMinutes(5))).thenReturn(true);

        service.onDeleted(new ProductDeletedEvent("e3", "PRODUCT_DELETED", "P1", 3, Instant.now(), "r1", "t1"));

        verify(vectorStore).delete("P1");
    }
}
