package com.vincent.aiservice.kafka;

import com.vincent.aiservice.kafka.event.OrderCreatedEvent;
import com.vincent.aiservice.observability.MdcKeys;
import com.vincent.aiservice.observability.MdcSupport;
import com.vincent.aiservice.service.AiMetrics;
import com.vincent.aiservice.service.InteractionSignalService;
import com.vincent.aiservice.service.RecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reacts to confirmed orders to update per-user behaviour signals and refresh recommendations.
 *
 * <p>At-least-once delivery — handling is made idempotent by deduplicating on {@code eventId}
 * (see {@link EventDeduplicator}) so Kafka redelivery does not double-count signals. A purchase
 * is the strongest signal we record.
 */
@Component
public class OrderCreatedKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedKafkaConsumer.class);

    private static final String INTERACTION_TYPE = "ORDER";
    private static final int ORDER_SIGNAL_WEIGHT = 5;

    private final JsonMapper jsonMapper;
    private final InteractionSignalService interactionSignalService;
    private final RecommendationService recommendationService;
    private final EventDeduplicator deduplicator;
    private final AiMetrics aiMetrics;

    public OrderCreatedKafkaConsumer(
            JsonMapper jsonMapper,
            InteractionSignalService interactionSignalService,
            RecommendationService recommendationService,
            EventDeduplicator deduplicator,
            AiMetrics aiMetrics
    ) {
        this.jsonMapper = jsonMapper;
        this.interactionSignalService = interactionSignalService;
        this.recommendationService = recommendationService;
        this.deduplicator = deduplicator;
        this.aiMetrics = aiMetrics;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.order-created}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "aiKafkaListenerContainerFactory"
    )
    public void onOrderCreated(String payload) {
        try {
            OrderCreatedEvent event = jsonMapper.readValue(payload, OrderCreatedEvent.class);
            applyMdc(event);
            log.info("ORDER_CREATED received orderNo={} eventId={}", event.orderNo(), event.eventId());

            if (!StringUtils.hasText(event.username())) {
                log.debug("ORDER_CREATED skipped (no user identity) orderNo={}", event.orderNo());
            } else if (!deduplicator.isDuplicate(event.eventId())) {
                interactionSignalService.recordInteraction(
                        event.username(), event.productCode(), INTERACTION_TYPE, ORDER_SIGNAL_WEIGHT);
                recommendationService.evictRecommendations(event.username());
            }
            aiMetrics.recordKafkaEventConsumed();
        } catch (Exception ex) {
            aiMetrics.recordKafkaConsumeFailure();
            log.error("ORDER_CREATED processing failed payload={}", payload, ex);
            throw new IllegalStateException("order-created processing failed", ex);
        } finally {
            clearMdc();
        }
    }

    private static void applyMdc(OrderCreatedEvent event) {
        MdcSupport.put(MdcKeys.REQUEST_ID, event.requestId());
        MdcSupport.put(MdcKeys.TRACE_ID, event.traceId());
        MdcSupport.putBusinessContext(event.username(), event.productCode(), event.eventId());
    }

    private static void clearMdc() {
        MdcSupport.remove(MdcKeys.REQUEST_ID);
        MdcSupport.remove(MdcKeys.TRACE_ID);
        MdcSupport.clearBusinessContext();
    }
}
