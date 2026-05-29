package com.vincent.aiservice.rag.embedding;

import java.util.List;

/**
 * Turns text into dense vectors. Abstraction boundary so the embedding backend (OpenAI today,
 * local model tomorrow) can be swapped without touching sync or retrieval logic.
 */
public interface EmbeddingProvider {

    /** Identifier of the embedding model/backend, for metrics and consistency checks. */
    String model();

    /** Embed a single text. */
    float[] embed(String text);

    /** Embed a batch of texts (order preserved). */
    List<float[]> embed(List<String> texts);
}
