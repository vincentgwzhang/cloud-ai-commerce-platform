package com.vincent.aiservice.ai;

import com.vincent.aiservice.dto.RecommendationItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Default local heuristic provider — no external LLM dependency.
 *
 * <p>Acts as the always-available baseline and degradation fallback. A real LLM-backed
 * {@link AiProvider} can be added later and selected via {@code app.ai.provider} without
 * touching the controller/service layers.
 */
@Component
public class StubAiProvider implements AiProvider {

    public static final String PROVIDER_NAME = "STUB";

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public List<RecommendationItem> recommend(String username, List<String> signalProductCodes, int maxItems) {
        // TODO: derive recommendations from signals (popularity / co-occurrence heuristic)
        return List.of();
    }

    @Override
    public String chat(String username, String message) {
        if (message == null || message.isBlank()) {
            return "Hi! How can I help you with your shopping today?";
        }
        String trimmed = message.strip();
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("hi") || normalized.startsWith("hello") || normalized.contains("你好")) {
            return "Hello! I'm your shopping assistant. Ask me about products, orders, or recommendations.";
        }
        return "You said: \"" + trimmed
                + "\". I'm a baseline assistant for now — richer answers arrive once an LLM provider is enabled.";
    }
}
