package com.vincent.aiservice.service.impl;

import com.vincent.aiservice.ai.AiProvider;
import com.vincent.aiservice.cache.RecommendationQueryCache;
import com.vincent.aiservice.config.AiProperties;
import com.vincent.aiservice.dto.RecommendationItem;
import com.vincent.aiservice.dto.RecommendationResponse;
import com.vincent.aiservice.entity.AiRecommendationLog;
import com.vincent.aiservice.repository.AiRecommendationLogRepository;
import com.vincent.aiservice.service.AiMetrics;
import com.vincent.aiservice.service.InteractionSignalService;
import com.vincent.aiservice.service.RecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrates recommendations: cache-aside lookup → gather behaviour signals → rank through the
 * pluggable {@link AiProvider} → audit-log the result.
 *
 * <p>The cache and audit log are auxiliary; the source of truth is the signal store (MySQL).
 */
@Service
public class RecommendationServiceImpl implements RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationServiceImpl.class);

    private static final int MAX_CONTEXT_LENGTH = 200;
    private static final int MAX_CODES_LENGTH = 500;

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
        aiMetrics.recordRecommendationRequest();

        var cached = recommendationQueryCache.find(username);
        if (cached.isPresent()) {
            aiMetrics.recordRecommendationCacheHit();
            log.debug("RECO cache hit username={}", username);
            return cached.get();
        }
        aiMetrics.recordRecommendationCacheMiss();

        List<String> seeds = interactionSignalService.recentSignalProductCodes(username);
        List<RecommendationItem> items = aiProvider.recommend(username, seeds, aiProperties.maxRecommendations());
        RecommendationResponse response = new RecommendationResponse(
                username, aiProvider.name(), items, Instant.now());

        recommendationQueryCache.put(username, response);
        auditLog(username, context, items);
        log.info("RECO generated username={} provider={} seeds={} items={}",
                username, aiProvider.name(), seeds.size(), items.size());
        return response;
    }

    @Override
    public RecommendationResponse recommendForProduct(String username, String productCode) {
        aiMetrics.recordRecommendationRequest();
        List<RecommendationItem> items =
                aiProvider.recommend(username, List.of(productCode), aiProperties.maxRecommendations());
        RecommendationResponse response = new RecommendationResponse(
                username, aiProvider.name(), items, Instant.now());
        auditLog(username, "product:" + productCode, items);
        log.info("RECO for-product username={} productCode={} items={}", username, productCode, items.size());
        return response;
    }

    @Override
    public void evictRecommendations(String username) {
        recommendationQueryCache.evict(username);
    }

    private void auditLog(String username, String context, List<RecommendationItem> items) {
        try {
            AiRecommendationLog entry = new AiRecommendationLog();
            entry.setUsername(username);
            entry.setContext(truncate(context, MAX_CONTEXT_LENGTH));
            entry.setRecommendedCodes(truncate(joinCodes(items), MAX_CODES_LENGTH));
            entry.setSource(aiProvider.name());
            recommendationLogRepository.save(entry);
        } catch (Exception ex) {
            log.warn("RECO audit-log skipped username={} error={}", username, ex.getMessage());
        }
    }

    private static String joinCodes(List<RecommendationItem> items) {
        return items.stream().map(RecommendationItem::productCode).collect(Collectors.joining(","));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
