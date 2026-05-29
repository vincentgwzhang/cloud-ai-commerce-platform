package com.vincent.aiservice.kafka;

import com.vincent.aiservice.service.AiMetrics;
import com.vincent.aiservice.service.InteractionSignalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Optional richer signal — reservation success can be weighted higher than a raw order event.
 */
@Component
public class InventoryReservedKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservedKafkaConsumer.class);

    private final JsonMapper jsonMapper;
    private final InteractionSignalService interactionSignalService;
    private final AiMetrics aiMetrics;

    public InventoryReservedKafkaConsumer(
            JsonMapper jsonMapper,
            InteractionSignalService interactionSignalService,
            AiMetrics aiMetrics
    ) {
        this.jsonMapper = jsonMapper;
        this.interactionSignalService = interactionSignalService;
        this.aiMetrics = aiMetrics;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.inventory-reserved}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "aiKafkaListenerContainerFactory"
    )
    public void onInventoryReserved(String payload) {
        // TODO: deserialize InventoryReservedEvent, dedup on eventId, record RESERVED signal
    }
}
