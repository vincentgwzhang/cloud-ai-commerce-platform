package com.vincent.productservice.kafka;

import com.vincent.productservice.config.ProductKafkaProperties;
import com.vincent.productservice.kafka.event.ProductCreatedEvent;
import com.vincent.productservice.kafka.event.ProductDeletedEvent;
import com.vincent.productservice.kafka.event.ProductUpdatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductEventPublisherTest {

    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final ProductEventPublisher publisher = new ProductEventPublisher(
            kafkaTemplate,
            JsonMapper.builder().build(),
            new ProductKafkaProperties(new ProductKafkaProperties.Topics("product-created", "product-updated", "product-deleted"))
    );

    @Test
    void publishCreatedSendsJsonToCreatedTopic() {
        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishCreated(createdEvent());

        verify(kafkaTemplate).send(eq("product-created"), eq("P1"), contains("\"eventType\":\"PRODUCT_CREATED\""));
    }

    @Test
    void publishUpdatedSendsJsonToUpdatedTopic() {
        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishUpdated(new ProductUpdatedEvent(
                "e2", "PRODUCT_UPDATED", "P1", "Phone", "Desc", BigDecimal.TEN,
                "ACTIVE", 2, Instant.parse("2026-01-01T00:00:00Z"), "r1", "t1"
        ));

        verify(kafkaTemplate).send(eq("product-updated"), eq("P1"), contains("\"eventType\":\"PRODUCT_UPDATED\""));
    }

    @Test
    void publishDeletedSendsJsonToDeletedTopic() {
        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishDeleted(new ProductDeletedEvent(
                "e3", "PRODUCT_DELETED", "P1", 3, Instant.parse("2026-01-01T00:00:00Z"), "r1", "t1"
        ));

        verify(kafkaTemplate).send(eq("product-deleted"), eq("P1"), contains("\"eventType\":\"PRODUCT_DELETED\""));
    }

    private static ProductCreatedEvent createdEvent() {
        return new ProductCreatedEvent(
                "e1", "PRODUCT_CREATED", "P1", "Phone", "Desc", BigDecimal.TEN,
                "ACTIVE", 1, Instant.parse("2026-01-01T00:00:00Z"), "r1", "t1"
        );
    }
}
