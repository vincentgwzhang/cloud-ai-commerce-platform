package com.vincent.aiservice.cache;

import com.vincent.aiservice.config.AiProperties;
import com.vincent.aiservice.dto.RecommendationResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

/**
 * Cache-aside store for per-user recommendation results.
 *
 * <p>Redis is auxiliary: every read/write is wrapped by {@link RedisSafeExecutor} so a Redis
 * outage degrades to live recomputation instead of failing the request.
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
        // TODO: read ai:reco:{username} from Redis and deserialize via jsonMapper
        return Optional.empty();
    }

    public void put(String username, RecommendationResponse response) {
        // TODO: serialize response and SET ai:reco:{username} with RedisTtlJitter-applied TTL
    }

    public void evict(String username) {
        // TODO: DEL ai:reco:{username}
    }
}
