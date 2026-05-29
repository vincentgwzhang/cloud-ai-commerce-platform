package com.vincent.aiservice.rag.sync;

import com.vincent.aiservice.kafka.event.ProductCreatedEvent;
import com.vincent.aiservice.kafka.event.ProductDeletedEvent;
import com.vincent.aiservice.kafka.event.ProductUpdatedEvent;

/**
 * Keeps the vector knowledge base in sync with product-service events.
 *
 * <p>ai-service holds only a derived, read-only projection — it never writes back to
 * product-service. Operations are idempotent so Kafka at-least-once redelivery is safe.
 */
public interface ProductVectorSyncService {

    void onCreated(ProductCreatedEvent event);

    void onUpdated(ProductUpdatedEvent event);

    void onDeleted(ProductDeletedEvent event);
}
