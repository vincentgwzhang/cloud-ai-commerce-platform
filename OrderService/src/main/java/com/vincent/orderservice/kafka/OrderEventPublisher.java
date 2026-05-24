package com.vincent.orderservice.kafka;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.vincent.orderservice.config.OrderKafkaProperties;
import com.vincent.orderservice.kafka.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka producer for order lifecycle events.
 * TODO: future outbox pattern — write event to outbox table in same DB transaction as order insert,
 * then a relay publishes to Kafka (avoids lost messages if broker is down after commit).
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final OrderKafkaProperties kafkaProperties;

    public OrderEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            OrderKafkaProperties kafkaProperties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.kafkaProperties = kafkaProperties;
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
        try {
            String json = jsonMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, json);
            log.debug("Published to {} key={}", topic, key);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize Kafka payload for topic " + topic, ex);
        }
    }
}
