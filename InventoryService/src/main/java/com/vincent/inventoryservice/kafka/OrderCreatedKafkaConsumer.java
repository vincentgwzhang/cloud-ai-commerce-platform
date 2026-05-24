package com.vincent.inventoryservice.kafka;

import tools.jackson.databind.json.JsonMapper;
import com.vincent.inventoryservice.kafka.event.OrderCreatedEvent;
import com.vincent.inventoryservice.service.InventoryKafkaHandler;
import com.vincent.inventoryservice.service.InventoryMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Saga choreography (step 1 — inventory side): react to new orders asynchronously.
 *
 * <p>Eventual consistency: the order row may exist before stock is reserved; order status
 * updates only after we publish {@code inventory-reserved} or {@code inventory-failed}.
 *
 * <p>At-least-once delivery + idempotent {@code reserve()} (Redis SETNX on requestId) prevents
 * double reservation when Kafka redelivers the same message.
 */
@Component
public class OrderCreatedKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedKafkaConsumer.class);

    private final JsonMapper jsonMapper;
    private final InventoryKafkaHandler inventoryKafkaHandler;
    private final InventoryMetrics metrics;

    public OrderCreatedKafkaConsumer(
            JsonMapper jsonMapper,
            InventoryKafkaHandler inventoryKafkaHandler,
            InventoryMetrics metrics
    ) {
        this.jsonMapper = jsonMapper;
        this.inventoryKafkaHandler = inventoryKafkaHandler;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.order-created}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "inventoryKafkaListenerContainerFactory"
    )
    public void onOrderCreated(String payload) {
        try {
            OrderCreatedEvent event = jsonMapper.readValue(payload, OrderCreatedEvent.class);
            log.info("ORDER_CREATED received orderNo={} eventId={}", event.orderNo(), event.eventId());
            inventoryKafkaHandler.handleOrderCreated(event);
            metrics.recordOrderCreatedEventConsumed();
        } catch (Exception ex) {
            metrics.recordKafkaConsumeFailure();
            log.error("Failed to process order-created: {}", payload, ex);
            throw new IllegalStateException("order-created processing failed", ex);
        }
    }
}
