package com.vincent.inventoryservice.kafka;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.vincent.inventoryservice.config.InventoryKafkaProperties;
import com.vincent.inventoryservice.kafka.event.InventoryFailedEvent;
import com.vincent.inventoryservice.kafka.event.InventoryReservedEvent;
import com.vincent.inventoryservice.observability.MdcSupport;
import com.vincent.inventoryservice.service.InventoryMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes inventory outcome events so order-service can advance the Saga choreography.
 *
 * <p>Async messaging decouples services: inventory does not need order-service's URL or uptime
 * at reservation time — only a shared topic contract.
 */
@Component
public class InventoryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final InventoryKafkaProperties kafkaProperties;
    private final InventoryMetrics metrics;

    public InventoryEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            InventoryKafkaProperties kafkaProperties,
            InventoryMetrics metrics
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.kafkaProperties = kafkaProperties;
        this.metrics = metrics;
    }

    public void publishReserved(InventoryReservedEvent event) {
        publish(kafkaProperties.topics().inventoryReserved(), event.orderNo(), event);
    }

    public void publishFailed(InventoryFailedEvent event) {
        publish(kafkaProperties.topics().inventoryFailed(), event.orderNo(), event);
    }

    private void publish(String topic, String key, Object payload) {
        long startNanos = System.nanoTime();
        try {
            String json = jsonMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, json).whenComplete((result, ex) -> {
                long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
                String requestId = MdcSupport.requestId().orElse("n/a");
                if (ex != null) {
                    log.error("Kafka publish failed topic={} key={} requestId={} durationMs={}",
                            topic, key, requestId, durationMs, ex);
                } else {
                    metrics.recordInventoryEventPublished();
                    log.info("Kafka publish ok topic={} key={} requestId={} durationMs={}",
                            topic, key, requestId, durationMs);
                }
            });
        } catch (JacksonException ex) {
            log.error("Kafka publish serialization failed topic={} key={}", topic, key, ex);
            throw new IllegalStateException("Failed to serialize Kafka payload for topic " + topic, ex);
        }
    }
}
