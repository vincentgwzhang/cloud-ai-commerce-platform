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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationServiceImplTest {

    private final AiProvider aiProvider = mock(AiProvider.class);
    private final RecommendationQueryCache cache = mock(RecommendationQueryCache.class);
    private final InteractionSignalService signals = mock(InteractionSignalService.class);
    private final AiRecommendationLogRepository logRepository = mock(AiRecommendationLogRepository.class);
    private final RecommendationServiceImpl service = new RecommendationServiceImpl(
            aiProvider,
            cache,
            signals,
            logRepository,
            new AiProperties(Duration.ofMinutes(10), 0, 2, "stub"),
            new AiMetrics(new SimpleMeterRegistry())
    );

    @Test
    void recommendReturnsCachedResponseWhenPresent() {
        RecommendationResponse cached = new RecommendationResponse("u1", "stub", List.of(), Instant.now());
        when(cache.find("u1")).thenReturn(Optional.of(cached));

        assertThat(service.recommend("u1", "ctx")).isSameAs(cached);

        verify(aiProvider, never()).recommend(any(), any(), any(Integer.class));
    }

    @Test
    void recommendGeneratesCachesAndAuditsWhenCacheMisses() {
        List<RecommendationItem> items = List.of(new RecommendationItem("P1", 0.9, "why"));
        when(cache.find("u1")).thenReturn(Optional.empty());
        when(signals.recentSignalProductCodes("u1")).thenReturn(List.of("SEED"));
        when(aiProvider.recommend("u1", List.of("SEED"), 2)).thenReturn(items);
        when(aiProvider.name()).thenReturn("stub");

        RecommendationResponse response = service.recommend("u1", "context");

        assertThat(response.items()).isEqualTo(items);
        verify(cache).put(eq("u1"), any(RecommendationResponse.class));
        verify(logRepository).save(any(AiRecommendationLog.class));
    }

    @Test
    void recommendForProductUsesProductAsSeedAndDoesNotCache() {
        List<RecommendationItem> items = List.of(new RecommendationItem("P2", 0.8, "related"));
        when(aiProvider.recommend("u1", List.of("P1"), 2)).thenReturn(items);
        when(aiProvider.name()).thenReturn("stub");

        RecommendationResponse response = service.recommendForProduct("u1", "P1");

        assertThat(response.items()).isEqualTo(items);
        verify(cache, never()).put(any(), any());
        verify(logRepository).save(any(AiRecommendationLog.class));
    }

    @Test
    void evictRecommendationsDelegatesToCache() {
        service.evictRecommendations("u1");

        verify(cache).evict("u1");
    }
}
