package com.vincent.inventoryservice.kafka;

import tools.jackson.databind.json.JsonMapper;
import com.vincent.inventoryservice.kafka.event.OrderCreatedEvent;
import com.vincent.inventoryservice.observability.MdcKeys;
import com.vincent.inventoryservice.observability.MdcSupport;
import com.vincent.inventoryservice.service.InventoryKafkaHandler;
import com.vincent.inventoryservice.service.InventoryMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
        long startNanos = System.nanoTime();
        try {
            OrderCreatedEvent event = jsonMapper.readValue(payload, OrderCreatedEvent.class);
            applyMdcFromEvent(event);
            log.info("ORDER_CREATED received orderNo={} eventId={}", event.orderNo(), event.eventId());
            inventoryKafkaHandler.handleOrderCreated(event);
            metrics.recordOrderCreatedEventConsumed();
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.info("ORDER_CREATED processed orderNo={} durationMs={}", event.orderNo(), durationMs);
        } catch (Exception ex) {
            metrics.recordKafkaConsumeFailure();
            log.error("Failed to process order-created: {}", payload, ex);
            throw new IllegalStateException("order-created processing failed", ex);
        } finally {
            clearConsumerMdc();
        }
    }

    private static void applyMdcFromEvent(OrderCreatedEvent event) {
        if (StringUtils.hasText(event.requestId())) {
            MDC.put(MdcKeys.REQUEST_ID, event.requestId());
        }
        if (StringUtils.hasText(event.traceId())) {
            MDC.put(MdcKeys.TRACE_ID, event.traceId());
        }
        MdcSupport.putBusinessContext(event.orderNo(), event.productCode(), event.eventId());
    }

    private static void clearConsumerMdc() {
        MDC.remove(MdcKeys.REQUEST_ID);
        MDC.remove(MdcKeys.TRACE_ID);
        MdcSupport.clearBusinessContext();
    }
}
