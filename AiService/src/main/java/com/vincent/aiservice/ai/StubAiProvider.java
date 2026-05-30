package com.vincent.aiservice.ai;

import com.vincent.aiservice.dto.RecommendationItem;
import com.vincent.aiservice.service.InteractionSignalService;
import com.vincent.aiservice.service.ScoredProduct;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Default local heuristic provider — no external LLM dependency.
 *
 * <p>Acts as the always-available baseline and degradation fallback. A real LLM-backed
 * {@link AiProvider} can be added later and selected via {@code app.ai.provider} without
 * touching the controller/service layers.
 *
 * <p>Recommendations are fully explainable: user-based collaborative filtering ("shoppers like
 * you") with a global-popularity fallback for cold start. Scores are normalized to {@code [0,1]}
 * so they are comparable regardless of how much history exists.
 */
@Component
public class StubAiProvider implements AiProvider {

    public static final String PROVIDER_NAME = "STUB";

    private static final String REASON_COLLABORATIVE = "Shoppers with a similar purchase history also chose this";
    private static final String REASON_POPULAR = "Popular across recent orders";

    private final InteractionSignalService signalService;

    public StubAiProvider(InteractionSignalService signalService) {
        this.signalService = signalService;
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public List<RecommendationItem> recommend(String username, List<String> signalProductCodes, int maxItems) {
        List<ScoredProduct> candidates = signalService.coOccurringProducts(username, signalProductCodes, maxItems);
        String reason = REASON_COLLABORATIVE;
        if (candidates.isEmpty()) {
            candidates = signalService.popularProducts(signalProductCodes, maxItems);
            reason = REASON_POPULAR;
        }
        double maxScore = candidates.stream().mapToDouble(ScoredProduct::score).max().orElse(1.0);
        double divisor = maxScore <= 0 ? 1.0 : maxScore;
        String explanation = reason;
        return candidates.stream()
                .map(candidate -> new RecommendationItem(
                        candidate.productCode(),
                        round(candidate.score() / divisor),
                        explanation))
                .toList();
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

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
