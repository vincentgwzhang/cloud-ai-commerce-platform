package com.vincent.aiservice.service.impl;

import com.vincent.aiservice.ai.AiProvider;
import com.vincent.aiservice.cache.RecommendationQueryCache;
import com.vincent.aiservice.config.AiProperties;
import com.vincent.aiservice.dto.RecommendationResponse;
import com.vincent.aiservice.repository.AiRecommendationLogRepository;
import com.vincent.aiservice.service.AiMetrics;
import com.vincent.aiservice.service.InteractionSignalService;
import com.vincent.aiservice.service.RecommendationService;
import org.springframework.stereotype.Service;

@Service
public class RecommendationServiceImpl implements RecommendationService {

    private final AiProvider aiProvider;
    private final RecommendationQueryCache recommendationQueryCache;
    private final InteractionSignalService interactionSignalService;
    private final AiRecommendationLogRepository recommendationLogRepository;
    private final AiProperties aiProperties;
    private final AiMetrics aiMetrics;

    public RecommendationServiceImpl(
            AiProvider aiProvider,
            RecommendationQueryCache recommendationQueryCache,
            InteractionSignalService interactionSignalService,
            AiRecommendationLogRepository recommendationLogRepository,
            AiProperties aiProperties,
            AiMetrics aiMetrics
    ) {
        this.aiProvider = aiProvider;
        this.recommendationQueryCache = recommendationQueryCache;
        this.interactionSignalService = interactionSignalService;
        this.recommendationLogRepository = recommendationLogRepository;
        this.aiProperties = aiProperties;
        this.aiMetrics = aiMetrics;
    }

    @Override
    public RecommendationResponse recommend(String username, String context) {
        // TODO: cache-aside lookup -> gather signals -> aiProvider.recommend -> log + cache result
        return null;
    }

    @Override
    public RecommendationResponse recommendForProduct(String username, String productCode) {
        // TODO: build "related products" recommendation seeded by productCode
        return null;
    }

    @Override
    public void evictRecommendations(String username) {
        // TODO: invalidate cached recommendations when user behaviour changes
    }
}
