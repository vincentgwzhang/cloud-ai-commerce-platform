package com.vincent.aiservice.ai;

import com.vincent.aiservice.dto.RecommendationItem;

import java.util.List;

/**
 * Abstraction over the underlying AI capability (heuristic stub today, LLM tomorrow).
 *
 * <p>Keeping generation behind this interface lets {@code app.ai.provider} swap the backend
 * without touching controller/service layers.
 */
public interface AiProvider {

    /** Identifier of this provider (e.g. {@code STUB}, {@code OPENAI}). */
    String name();

    /** Produce recommended products for a user given recent behaviour signals. */
    List<RecommendationItem> recommend(String username, List<String> signalProductCodes, int maxItems);

    /** Produce a shopping-assistant reply for a user message. */
    String chat(String username, String message);
}
