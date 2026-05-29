package com.vincent.aiservice.rag.vector;

import java.util.Map;

/**
 * A product's vector record to upsert into the store.
 *
 * @param productCode business key (vector id)
 * @param embedding   dense vector of the document text
 * @param document    canonical text that was embedded (stored for retrieval context)
 * @param metadata    lightweight read-only projection (name/price/status/...)
 */
public record ProductVector(
        String productCode,
        float[] embedding,
        String document,
        Map<String, Object> metadata
) {
}
