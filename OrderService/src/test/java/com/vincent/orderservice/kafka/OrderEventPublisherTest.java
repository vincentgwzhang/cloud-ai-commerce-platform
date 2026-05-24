package com.vincent.orderservice.kafka;

import tools.jackson.databind.json.JsonMapper;
import com.vincent.orderservice.config.OrderKafkaProperties;
import com.vincent.orderservice.kafka.event.OrderCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OrderEventPublisher publisher;

    @BeforeEach
    void setUp() {
        OrderKafkaProperties.Topics topics = new OrderKafkaProperties.Topics(
                "order-created", "order-confirmed", "order-failed",
                "inventory-reserved", "inventory-failed", "order-dlq"
        );
        OrderKafkaProperties properties = new OrderKafkaProperties(topics, 3, 1000L);
        publisher = new OrderEventPublisher(
                kafkaTemplate,
                JsonMapper.builder().findAndAddModules().build(),
                properties
        );
    }

    @Test
    void publishOrderCreated() {
        publisher.publishOrderCreated(
                new OrderCreatedEvent("ORD-1", "IPHONE17", 1, new BigDecimal("999"), "req-1")
        );
        verify(kafkaTemplate).send(eq("order-created"), eq("ORD-1"), org.mockito.ArgumentMatchers.anyString());
    }
}
