package com.vincent.inventoryservice.kafka;

import tools.jackson.databind.json.JsonMapper;
import com.vincent.inventoryservice.config.InventoryKafkaProperties;
import com.vincent.inventoryservice.kafka.event.InventoryReservedEvent;
import com.vincent.inventoryservice.service.InventoryMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private InventoryMetrics metrics;

    private InventoryEventPublisher publisher;

    @BeforeEach
    void setUp() {
        InventoryKafkaProperties.Topics topics = new InventoryKafkaProperties.Topics(
                "order-created", "inventory-reserved", "inventory-failed", "inventory-dlq"
        );
        InventoryKafkaProperties properties = new InventoryKafkaProperties(topics, 3, 1000L);
        publisher = new InventoryEventPublisher(
                kafkaTemplate,
                JsonMapper.builder().findAndAddModules().build(),
                properties,
                metrics
        );
    }

    @Test
    void publishReserved() {
        when(kafkaTemplate.send(eq("inventory-reserved"), eq("ORD-1"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishReserved(InventoryReservedEvent.of("ORD-1", "IPHONE17", 1));

        verify(kafkaTemplate).send(eq("inventory-reserved"), eq("ORD-1"), org.mockito.ArgumentMatchers.anyString());
        verify(metrics).recordInventoryEventPublished();
    }
}
