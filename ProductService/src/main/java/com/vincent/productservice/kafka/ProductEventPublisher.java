package com.vincent.productservice.kafka;

import com.vincent.productservice.config.ProductKafkaProperties;
import com.vincent.productservice.kafka.event.ProductCreatedEvent;
import com.vincent.productservice.kafka.event.ProductDeletedEvent;
import com.vincent.productservice.kafka.event.ProductUpdatedEvent;
import com.vincent.productservice.observability.MdcSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ProductEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProductEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final ProductKafkaProperties kafkaProperties;

    public ProductEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            ProductKafkaProperties kafkaProperties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.kafkaProperties = kafkaProperties;
    }

    public void publishCreated(ProductCreatedEvent event) {
        publish(kafkaProperties.topics().productCreated(), event.productCode(), event);
    }

    public void publishUpdated(ProductUpdatedEvent event) {
        publish(kafkaProperties.topics().productUpdated(), event.productCode(), event);
    }

    public void publishDeleted(ProductDeletedEvent event) {
        publish(kafkaProperties.topics().productDeleted(), event.productCode(), event);
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
