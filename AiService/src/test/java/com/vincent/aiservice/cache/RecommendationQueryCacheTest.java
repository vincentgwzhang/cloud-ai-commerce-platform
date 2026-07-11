package com.vincent.aiservice.cache;

import com.vincent.aiservice.config.AiProperties;
import com.vincent.aiservice.dto.RecommendationItem;
import com.vincent.aiservice.dto.RecommendationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationQueryCacheTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private RecommendationQueryCache cache;

    @BeforeEach
    void setUp() {
        cache = new RecommendationQueryCache(
                redisTemplate,
                jsonMapper,
                new AiProperties(Duration.ofMinutes(10), 0, 3, "stub")
        );
    }

    @Test
    void findReturnsDeserializedRecommendationWhenPresent() throws Exception {
        RecommendationResponse response = response();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(AiRedisKeys.recommendation("u1"))).thenReturn(jsonMapper.writeValueAsString(response));

        Optional<RecommendationResponse> result = cache.find("u1");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().items()).extracting(RecommendationItem::productCode).containsExactly("P1");
    }

    @Test
    void putSerializesWithConfiguredTtlAndEvictDeletesKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        cache.put("u1", response());
        cache.evict("u1");

        verify(valueOps).set(eq(AiRedisKeys.recommendation("u1")), org.mockito.ArgumentMatchers.contains("\"productCode\":\"P1\""), eq(Duration.ofMinutes(10)));
        verify(redisTemplate).delete(AiRedisKeys.recommendation("u1"));
    }

    @Test
    void findDegradesToEmptyWhenRedisFails() {
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis down"));

        assertThat(cache.find("u1")).isEmpty();
    }

    private static RecommendationResponse response() {
        return new RecommendationResponse(
                "u1",
                "generated",
                List.of(new RecommendationItem("P1", 0.9, "matched")),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}
