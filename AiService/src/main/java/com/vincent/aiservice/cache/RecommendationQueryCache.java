package com.vincent.aiservice.cache;

import com.vincent.aiservice.config.AiProperties;
import com.vincent.aiservice.dto.RecommendationResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;

/**
 * Cache-aside store for per-user recommendation results.
 *
 * <p>Redis is auxiliary: every read/write is wrapped by {@link RedisSafeExecutor} so a Redis
 * outage degrades to live recomputation instead of failing the request. A jittered TTL spreads
 * expirations to avoid a cache-avalanche when many keys were warmed together.
 */
@Component
public class RecommendationQueryCache {

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final AiProperties aiProperties;

    public RecommendationQueryCache(
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            AiProperties aiProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.aiProperties = aiProperties;
    }

    public Optional<RecommendationResponse> find(String username) {
        return RedisSafeExecutor.optional(() -> {
            String json = redisTemplate.opsForValue().get(AiRedisKeys.recommendation(username));
            return json == null ? null : jsonMapper.readValue(json, RecommendationResponse.class);
        });
    }

    public void put(String username, RecommendationResponse response) {
        RedisSafeExecutor.run(() -> {
            String json = jsonMapper.writeValueAsString(response);
            Duration ttl = RedisTtlJitter.apply(aiProperties.recommendationTtl(), aiProperties.ttlJitterMaxSeconds());
            redisTemplate.opsForValue().set(AiRedisKeys.recommendation(username), json, ttl);
        });
    }

    public void evict(String username) {
        RedisSafeExecutor.run(() -> redisTemplate.delete(AiRedisKeys.recommendation(username)));
    }
}
