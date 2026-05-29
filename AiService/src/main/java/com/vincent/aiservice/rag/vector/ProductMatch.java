package com.vincent.aiservice.rag.vector;

import java.util.Map;

/**
 * A similarity-search hit.
 *
 * @param productCode matched product business key
 * @param distance    raw distance from the store (lower = more similar)
 * @param document    stored document text for prompt context
 * @param metadata    read-only projection
 */
public record ProductMatch(
        String productCode,
        double distance,
        String document,
        Map<String, Object> metadata
) {
}
