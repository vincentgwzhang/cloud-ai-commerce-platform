package com.vincent.orderservice.kafka;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.vincent.orderservice.config.OrderKafkaProperties;
import com.vincent.orderservice.kafka.event.OrderCreatedEvent;
import com.vincent.orderservice.observability.MdcSupport;
import com.vincent.orderservice.service.OrderMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka producer for order lifecycle events.
 *
 * <p>Decoupling: inventory-service subscribes to {@code order-created} instead of order-service
 * calling it synchronously — each service can scale and deploy independently.
 *
 * <p>TODO: outbox pattern — write event to outbox table in same DB transaction as order insert,
 * then a relay publishes to Kafka (avoids lost messages if broker is down after commit).
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final OrderKafkaProperties kafkaProperties;
    private final OrderMetrics orderMetrics;

    public OrderEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            OrderKafkaProperties kafkaProperties,
            OrderMetrics orderMetrics
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.kafkaProperties = kafkaProperties;
        this.orderMetrics = orderMetrics;
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        publish(kafkaProperties.topics().orderCreated(), event.orderNo(), event);
    }

    public void publishOrderConfirmed(String orderNo, Object payload) {
        publish(kafkaProperties.topics().orderConfirmed(), orderNo, payload);
    }

    public void publishOrderFailed(String orderNo, Object payload) {
        publish(kafkaProperties.topics().orderFailed(), orderNo, payload);
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
                    orderMetrics.recordOrderEventPublished();
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
