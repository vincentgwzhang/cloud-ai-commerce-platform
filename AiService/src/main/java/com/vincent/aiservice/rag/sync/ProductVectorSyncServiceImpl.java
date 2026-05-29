package com.vincent.aiservice.rag.sync;

import com.vincent.aiservice.cache.AiRedisKeys;
import com.vincent.aiservice.config.RagProperties;
import com.vincent.aiservice.kafka.event.ProductCreatedEvent;
import com.vincent.aiservice.kafka.event.ProductDeletedEvent;
import com.vincent.aiservice.kafka.event.ProductUpdatedEvent;
import com.vincent.aiservice.rag.ProductDocumentFactory;
import com.vincent.aiservice.rag.embedding.EmbeddingProvider;
import com.vincent.aiservice.rag.vector.ProductVector;
import com.vincent.aiservice.rag.vector.ProductVectorStore;
import com.vincent.aiservice.service.AiMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Embeds product snapshots and upserts/deletes them in the vector store.
 *
 * <p>Idempotency: each {@code eventId} is recorded in Redis ({@code SETNX}); a redelivered event
 * is skipped. The upsert itself is keyed by {@code productCode}, so it converges regardless.
 */
@Service
public class ProductVectorSyncServiceImpl implements ProductVectorSyncService {

    private static final Logger log = LoggerFactory.getLogger(ProductVectorSyncServiceImpl.class);

    private final EmbeddingProvider embeddingProvider;
    private final ProductVectorStore vectorStore;
    private final StringRedisTemplate redisTemplate;
    private final RagProperties ragProperties;
    private final AiMetrics aiMetrics;

    public ProductVectorSyncServiceImpl(
            EmbeddingProvider embeddingProvider,
            ProductVectorStore vectorStore,
            StringRedisTemplate redisTemplate,
            RagProperties ragProperties,
            AiMetrics aiMetrics
    ) {
        this.embeddingProvider = embeddingProvider;
        this.vectorStore = vectorStore;
        this.redisTemplate = redisTemplate;
        this.ragProperties = ragProperties;
        this.aiMetrics = aiMetrics;
    }

    @Override
    public void onCreated(ProductCreatedEvent event) {
        if (isDuplicate(event.eventId())) {
            return;
        }
        upsert(event.productCode(), event.name(), event.description(), event.price(), event.status());
    }

    @Override
    public void onUpdated(ProductUpdatedEvent event) {
        if (isDuplicate(event.eventId())) {
            return;
        }
        upsert(event.productCode(), event.name(), event.description(), event.price(), event.status());
    }

    @Override
    public void onDeleted(ProductDeletedEvent event) {
        if (isDuplicate(event.eventId())) {
            return;
        }
        vectorStore.delete(event.productCode());
        aiMetrics.recordProductSync("delete");
        log.info("PRODUCT_SYNC delete productCode={}", event.productCode());
    }

    private void upsert(String productCode, String name, String description, BigDecimal price, String status) {
        String document = ProductDocumentFactory.canonicalText(productCode, name, description);
        float[] embedding = embeddingProvider.embed(document);
        vectorStore.upsert(new ProductVector(productCode, embedding, document, metadata(productCode, name, price, status)));
        aiMetrics.recordProductSync("upsert");
        log.info("PRODUCT_SYNC upsert productCode={} model={}", productCode, embeddingProvider.model());
    }

    private boolean isDuplicate(String eventId) {
        Boolean firstSeen = redisTemplate.opsForValue()
                .setIfAbsent(AiRedisKeys.dedup(eventId), "1", ragProperties.dedupTtl());
        boolean duplicate = !Boolean.TRUE.equals(firstSeen);
        if (duplicate) {
            log.debug("PRODUCT_SYNC duplicate event skipped eventId={}", eventId);
        }
        return duplicate;
    }

    private static Map<String, Object> metadata(String productCode, String name, BigDecimal price, String status) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("productCode", productCode);
        if (name != null) {
            metadata.put("name", name);
        }
        if (price != null) {
            metadata.put("price", price.toPlainString());
        }
        if (status != null) {
            metadata.put("status", status);
        }
        return metadata;
    }
}
