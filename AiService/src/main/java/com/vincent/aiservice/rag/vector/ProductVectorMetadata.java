package com.vincent.aiservice.rag.vector;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Product metadata stored alongside the vector for filtering/debugging in Chroma.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductVectorMetadata(
        String productCode,
        String name,
        String price,
        String status
) {
}
