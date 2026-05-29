package com.vincent.aiservice.memory;

import com.vincent.aiservice.cache.AiRedisKeys;
import com.vincent.aiservice.config.ConversationMemoryProperties;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Redis-backed adapter for {@link ConversationMemoryRepository}.
 *
 * <p>Each conversation is a Redis list of JSON-encoded {@link ConversationMessage}s keyed by
 * {@code userId + conversationId}. Writes refresh a sliding TTL so idle conversations expire;
 * the list is trimmed to {@code app.ai.memory.max-messages} to bound memory growth.
 */
@Repository
public class RedisConversationMemoryRepository implements ConversationMemoryRepository {

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final ConversationMemoryProperties properties;

    public RedisConversationMemoryRepository(
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            ConversationMemoryProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
    }

    @Override
    public void append(String userId, String conversationId, ConversationMessage message) {
        String key = AiRedisKeys.conversation(userId, conversationId);
        ListOperations<String, String> listOps = redisTemplate.opsForList();
        listOps.rightPush(key, serialize(message));
        if (properties.maxMessages() > 0) {
            listOps.trim(key, -properties.maxMessages(), -1);
        }
        if (properties.ttl() != null) {
            redisTemplate.expire(key, properties.ttl());
        }
    }

    @Override
    public List<ConversationMessage> findMessages(String userId, String conversationId) {
        String key = AiRedisKeys.conversation(userId, conversationId);
        List<String> raw = redisTemplate.opsForList().range(key, 0, -1);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream().map(this::deserialize).toList();
    }

    @Override
    public boolean exists(String userId, String conversationId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(AiRedisKeys.conversation(userId, conversationId)));
    }

    @Override
    public void delete(String userId, String conversationId) {
        redisTemplate.delete(AiRedisKeys.conversation(userId, conversationId));
    }

    private String serialize(ConversationMessage message) {
        try {
            return jsonMapper.writeValueAsString(message);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize conversation message", ex);
        }
    }

    private ConversationMessage deserialize(String json) {
        try {
            return jsonMapper.readValue(json, ConversationMessage.class);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to deserialize conversation message", ex);
        }
    }
}
