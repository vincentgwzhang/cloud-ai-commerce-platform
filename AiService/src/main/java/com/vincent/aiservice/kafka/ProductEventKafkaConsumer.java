package com.vincent.aiservice.kafka;

import com.vincent.aiservice.kafka.event.ProductCreatedEvent;
import com.vincent.aiservice.kafka.event.ProductDeletedEvent;
import com.vincent.aiservice.kafka.event.ProductUpdatedEvent;
import com.vincent.aiservice.observability.MdcKeys;
import com.vincent.aiservice.observability.MdcSupport;
import com.vincent.aiservice.rag.sync.ProductVectorSyncService;
import com.vincent.aiservice.service.AiMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes product lifecycle events and drives vector-store synchronization.
 *
 * <p>On unrecoverable errors the message is routed to {@code ai-dlq} by the shared error handler;
 * transient errors are retried first (see {@code KafkaConsumerConfig}).
 */
@Component
public class ProductEventKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProductEventKafkaConsumer.class);

    private final JsonMapper jsonMapper;
    private final ProductVectorSyncService syncService;
    private final AiMetrics aiMetrics;

    public ProductEventKafkaConsumer(
            JsonMapper jsonMapper,
            ProductVectorSyncService syncService,
            AiMetrics aiMetrics
    ) {
        this.jsonMapper = jsonMapper;
        this.syncService = syncService;
        this.aiMetrics = aiMetrics;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.product-created}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "aiKafkaListenerContainerFactory"
    )
    public void onProductCreated(String payload) {
        handle(payload, () -> {
            ProductCreatedEvent event = jsonMapper.readValue(payload, ProductCreatedEvent.class);
            applyMdc(event.productCode(), event.requestId(), event.traceId(), event.eventId());
            log.info("PRODUCT_CREATED received productCode={} eventId={}", event.productCode(), event.eventId());
            syncService.onCreated(event);
        });
    }

    @KafkaListener(
            topics = "${app.kafka.topics.product-updated}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "aiKafkaListenerContainerFactory"
    )
    public void onProductUpdated(String payload) {
        handle(payload, () -> {
            ProductUpdatedEvent event = jsonMapper.readValue(payload, ProductUpdatedEvent.class);
            applyMdc(event.productCode(), event.requestId(), event.traceId(), event.eventId());
            log.info("PRODUCT_UPDATED received productCode={} eventId={}", event.productCode(), event.eventId());
            syncService.onUpdated(event);
        });
    }

    @KafkaListener(
            topics = "${app.kafka.topics.product-deleted}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "aiKafkaListenerContainerFactory"
    )
    public void onProductDeleted(String payload) {
        handle(payload, () -> {
            ProductDeletedEvent event = jsonMapper.readValue(payload, ProductDeletedEvent.class);
            applyMdc(event.productCode(), event.requestId(), event.traceId(), event.eventId());
            log.info("PRODUCT_DELETED received productCode={} eventId={}", event.productCode(), event.eventId());
            syncService.onDeleted(event);
        });
    }

    private void handle(String payload, Runnable action) {
        try {
            action.run();
            aiMetrics.recordKafkaEventConsumed();
        } catch (Exception ex) {
            aiMetrics.recordKafkaConsumeFailure();
            log.error("PRODUCT_SYNC processing failed payload={}", payload, ex);
            throw new IllegalStateException("product event processing failed", ex);
        } finally {
            clearMdc();
        }
    }

    private static void applyMdc(String productCode, String requestId, String traceId, String eventId) {
        MdcSupport.put(MdcKeys.REQUEST_ID, requestId);
        MdcSupport.put(MdcKeys.TRACE_ID, traceId);
        MdcSupport.putBusinessContext(null, productCode, eventId);
    }

    private static void clearMdc() {
        MdcSupport.remove(MdcKeys.REQUEST_ID);
        MdcSupport.remove(MdcKeys.TRACE_ID);
        MdcSupport.clearBusinessContext();
    }
}
