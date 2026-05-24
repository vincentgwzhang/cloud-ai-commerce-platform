package com.vincent.inventoryservice.service;

import com.vincent.inventoryservice.dto.InventoryResponse;
import com.vincent.inventoryservice.exception.InsufficientInventoryException;
import com.vincent.inventoryservice.kafka.InventoryEventPublisher;
import com.vincent.inventoryservice.kafka.event.InventoryFailedEvent;
import com.vincent.inventoryservice.kafka.event.InventoryReservedEvent;
import com.vincent.inventoryservice.kafka.event.OrderCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryKafkaHandlerTest {

    @Mock
    private InventoryService inventoryService;

    @Mock
    private InventoryEventPublisher eventPublisher;

    @InjectMocks
    private InventoryKafkaHandler handler;

    @Test
    void reservesAndPublishesReservedEvent() {
        OrderCreatedEvent event = sampleEvent("req-kafka-1");
        when(inventoryService.reserve("IPHONE17", 1, "req-kafka-1"))
                .thenReturn(new InventoryResponse("IPHONE17", 9, 1, 1L));

        handler.handleOrderCreated(event);

        ArgumentCaptor<InventoryReservedEvent> captor = ArgumentCaptor.forClass(InventoryReservedEvent.class);
        verify(eventPublisher).publishReserved(captor.capture());
        assertThat(captor.getValue().orderNo()).isEqualTo("ORD-1");
        assertThat(captor.getValue().eventType()).isEqualTo(InventoryReservedEvent.EVENT_TYPE);
    }

    @Test
    void publishesFailedEventOnInsufficientStock() {
        OrderCreatedEvent event = sampleEvent("req-kafka-2");
        when(inventoryService.reserve(eq("IPHONE17"), eq(1), eq("req-kafka-2")))
                .thenThrow(new InsufficientInventoryException("IPHONE17", 1, 0));

        handler.handleOrderCreated(event);

        ArgumentCaptor<InventoryFailedEvent> captor = ArgumentCaptor.forClass(InventoryFailedEvent.class);
        verify(eventPublisher).publishFailed(captor.capture());
        assertThat(captor.getValue().orderNo()).isEqualTo("ORD-1");
    }

    private static OrderCreatedEvent sampleEvent(String requestId) {
        return new OrderCreatedEvent(
                "evt-1",
                "ORDER_CREATED",
                "ORD-1",
                "IPHONE17",
                1,
                new BigDecimal("999"),
                requestId,
                "trace-test",
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}
