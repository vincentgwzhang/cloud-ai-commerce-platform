package com.vincent.aiservice.service;

import java.util.Collection;
import java.util.List;

/**
 * Owns the behaviour-signal domain: records per-user interaction signals (derived from Kafka
 * events) and derives recommendation candidates from them.
 *
 * <p>This is the data-access side of the recommender. The actual ranking/explanation lives behind
 * {@link com.vincent.aiservice.ai.AiProvider} so the scoring backend can be swapped.
 */
public interface InteractionSignalService {

    /** Persist one weighted behaviour signal (e.g. ORDER, RESERVED) for a user. */
    void recordInteraction(String username, String productCode, String interactionType, int weight);

    /** Distinct product codes the user has interacted with, most recent first ("seeds"). */
    List<String> recentSignalProductCodes(String username);

    /**
     * User-based collaborative filtering: products favoured by other shoppers who share the
     * given seed products, excluding the user's own seeds. Empty when there is no overlap.
     */
    List<ScoredProduct> coOccurringProducts(String username, List<String> seeds, int maxItems);

    /** Global popularity fallback (cold start): top products by summed signal weight. */
    List<ScoredProduct> popularProducts(Collection<String> excludeProductCodes, int maxItems);
}
