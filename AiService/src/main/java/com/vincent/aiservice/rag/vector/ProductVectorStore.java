package com.vincent.aiservice.rag.vector;

import java.util.List;

/**
 * Vector storage port for the product knowledge base.
 *
 * <p>This is the seam that makes the vector database replaceable: swapping Chroma for another
 * store (pgvector, Redis, Qdrant, ...) means adding one new implementation of this interface —
 * no changes to sync or retrieval callers.
 */
public interface ProductVectorStore {

    /** Insert or replace a product's vector (idempotent by productCode). */
    void upsert(ProductVector vector);

    /** Remove a product's vector. */
    void delete(String productCode);

    /** Return the {@code topK} nearest products to the query embedding. */
    List<ProductMatch> similaritySearch(float[] queryEmbedding, int topK);
}
