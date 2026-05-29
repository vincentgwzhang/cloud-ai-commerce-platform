package com.vincent.aiservice.kafka;

import com.vincent.aiservice.service.AiMetrics;
import com.vincent.aiservice.service.InteractionSignalService;
import com.vincent.aiservice.service.RecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reacts to new orders to update per-user behaviour signals and refresh recommendations.
 *
 * <p>At-least-once delivery — handling must be idempotent (dedup on eventId) so Kafka redelivery
 * does not double-count signals.
 */
@Component
public class OrderCreatedKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedKafkaConsumer.class);

    private final JsonMapper jsonMapper;
    private final InteractionSignalService interactionSignalService;
    private final RecommendationService recommendationService;
    private final AiMetrics aiMetrics;

    public OrderCreatedKafkaConsumer(
            JsonMapper jsonMapper,
            InteractionSignalService interactionSignalService,
            RecommendationService recommendationService,
            AiMetrics aiMetrics
    ) {
        this.jsonMapper = jsonMapper;
        this.interactionSignalService = interactionSignalService;
        this.recommendationService = recommendationService;
        this.aiMetrics = aiMetrics;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.order-created}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "aiKafkaListenerContainerFactory"
    )
    public void onOrderCreated(String payload) {
        // TODO: deserialize OrderCreatedEvent, dedup on eventId, record ORDER signal, evict recommendations
    }
}
