package com.vincent.aiservice.kafka;

import com.vincent.aiservice.kafka.event.InventoryReservedEvent;
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
 * Secondary, weaker signal: a successful stock reservation indicates intent slightly earlier than
 * a confirmed order. Weighted below {@code ORDER} so confirmed purchases dominate the ranking.
 *
 * <p>Idempotent via {@link EventDeduplicator} (dedup on {@code eventId}).
 */
@Component
public class InventoryReservedKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservedKafkaConsumer.class);

    private static final String INTERACTION_TYPE = "RESERVED";
    private static final int RESERVED_SIGNAL_WEIGHT = 3;

    private final JsonMapper jsonMapper;
    private final InteractionSignalService interactionSignalService;
    private final RecommendationService recommendationService;
    private final EventDeduplicator deduplicator;
    private final AiMetrics aiMetrics;

    public InventoryReservedKafkaConsumer(
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
            topics = "${app.kafka.topics.inventory-reserved}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "aiKafkaListenerContainerFactory"
    )
    public void onInventoryReserved(String payload) {
        try {
            InventoryReservedEvent event = jsonMapper.readValue(payload, InventoryReservedEvent.class);
            MdcSupport.putBusinessContext(event.username(), event.productCode(), event.eventId());
            log.info("INVENTORY_RESERVED received orderNo={} eventId={}", event.orderNo(), event.eventId());

            if (!StringUtils.hasText(event.username())) {
                log.debug("INVENTORY_RESERVED skipped (no user identity) orderNo={}", event.orderNo());
            } else if (!deduplicator.isDuplicate(event.eventId())) {
                interactionSignalService.recordInteraction(
                        event.username(), event.productCode(), INTERACTION_TYPE, RESERVED_SIGNAL_WEIGHT);
                recommendationService.evictRecommendations(event.username());
            }
            aiMetrics.recordKafkaEventConsumed();
        } catch (Exception ex) {
            aiMetrics.recordKafkaConsumeFailure();
            log.error("INVENTORY_RESERVED processing failed payload={}", payload, ex);
            throw new IllegalStateException("inventory-reserved processing failed", ex);
        } finally {
            MdcSupport.clearBusinessContext();
        }
    }
}
