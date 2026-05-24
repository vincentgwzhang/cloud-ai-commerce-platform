package com.vincent.orderservice.kafka;

import tools.jackson.databind.json.JsonMapper;
import com.vincent.orderservice.kafka.event.InventoryFailedEvent;
import com.vincent.orderservice.kafka.event.InventoryReservedEvent;
import com.vincent.orderservice.service.OrderMetrics;
import com.vincent.orderservice.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Saga choreography (step 2): listen for inventory outcomes.
 * At-least-once delivery means handlers must be idempotent on orderNo/status transitions.
 * Retry + DLQ are configured on the listener container factory (see KafkaConsumerConfig).
 */
@Component
public class InventoryResultKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryResultKafkaConsumer.class);

    private final JsonMapper jsonMapper;
    private final OrderService orderService;
    private final OrderMetrics orderMetrics;

    public InventoryResultKafkaConsumer(
            JsonMapper jsonMapper,
            OrderService orderService,
            OrderMetrics orderMetrics
    ) {
        this.jsonMapper = jsonMapper;
        this.orderService = orderService;
        this.orderMetrics = orderMetrics;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.inventory-reserved}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "orderKafkaListenerContainerFactory"
    )
    public void onInventoryReserved(String payload) {
        try {
            InventoryReservedEvent event = jsonMapper.readValue(payload, InventoryReservedEvent.class);
            orderService.onInventoryReserved(event);
        } catch (Exception ex) {
            orderMetrics.recordKafkaConsumeFailure();
            log.error("Failed to process inventory-reserved: {}", payload, ex);
            throw new IllegalStateException("inventory-reserved processing failed", ex);
        }
    }

    @KafkaListener(
            topics = "${app.kafka.topics.inventory-failed}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "orderKafkaListenerContainerFactory"
    )
    public void onInventoryFailed(String payload) {
        try {
            InventoryFailedEvent event = jsonMapper.readValue(payload, InventoryFailedEvent.class);
            orderService.onInventoryFailed(event);
        } catch (Exception ex) {
            orderMetrics.recordKafkaConsumeFailure();
            log.error("Failed to process inventory-failed: {}", payload, ex);
            throw new IllegalStateException("inventory-failed processing failed", ex);
        }
    }
}
