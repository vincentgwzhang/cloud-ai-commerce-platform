package com.vincent.orderservice.kafka;

import tools.jackson.databind.json.JsonMapper;
import com.vincent.orderservice.config.OrderKafkaProperties;
import com.vincent.orderservice.entity.Order;
import com.vincent.orderservice.entity.OrderStatus;
import com.vincent.orderservice.kafka.event.OrderCreatedEvent;
import com.vincent.orderservice.service.OrderMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private OrderMetrics orderMetrics;

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
                properties,
                orderMetrics
        );
    }

    @Test
    void publishOrderCreated() {
        Order order = new Order();
        order.setOrderNo("ORD-1");
        order.setProductCode("IPHONE17");
        order.setQuantity(1);
        order.setAmount(new BigDecimal("999"));
        order.setRequestId("req-1");
        order.setStatus(OrderStatus.CREATED);

        when(kafkaTemplate.send(eq("order-created"), eq("ORD-1"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishOrderCreated(OrderCreatedEvent.from(order));

        verify(kafkaTemplate).send(eq("order-created"), eq("ORD-1"), org.mockito.ArgumentMatchers.anyString());
        verify(orderMetrics).recordOrderEventPublished();
    }
}
