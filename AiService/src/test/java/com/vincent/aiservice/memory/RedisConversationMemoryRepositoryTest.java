package com.vincent.aiservice.memory;

import com.vincent.aiservice.config.ConversationMemoryProperties;
import com.vincent.aiservice.cache.AiRedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisConversationMemoryRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOps;

    private RedisConversationMemoryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new RedisConversationMemoryRepository(
                redisTemplate,
                JsonMapper.builder().build(),
                new ConversationMemoryProperties(Duration.ofMinutes(30), 5)
        );
    }

    @Test
    void appendSerializesMessageTrimsAndRefreshesTtl() {
        when(redisTemplate.opsForList()).thenReturn(listOps);

        repository.append("u1", "c1", ConversationMessage.user("hello"));

        verify(listOps).rightPush(eq(AiRedisKeys.conversation("u1", "c1")), org.mockito.ArgumentMatchers.contains("\"content\":\"hello\""));
        verify(listOps).trim(AiRedisKeys.conversation("u1", "c1"), -5, -1);
        verify(redisTemplate).expire(AiRedisKeys.conversation("u1", "c1"), Duration.ofMinutes(30));
    }

    @Test
    void findMessagesDeserializesRedisList() {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(listOps.range(AiRedisKeys.conversation("u1", "c1"), 0, -1)).thenReturn(List.of(
                "{\"role\":\"USER\",\"content\":\"hello\",\"timestamp\":\"2026-01-01T00:00:00Z\"}",
                "{\"role\":\"ASSISTANT\",\"content\":\"hi\",\"timestamp\":\"2026-01-01T00:00:01Z\"}"
        ));

        List<ConversationMessage> messages = repository.findMessages("u1", "c1");

        assertThat(messages).extracting(ConversationMessage::role).containsExactly(MessageRole.USER, MessageRole.ASSISTANT);
        assertThat(messages).extracting(ConversationMessage::content).containsExactly("hello", "hi");
    }

    @Test
    void existsAndDeleteUseConversationKey() {
        when(redisTemplate.hasKey(AiRedisKeys.conversation("u1", "c1"))).thenReturn(true);

        assertThat(repository.exists("u1", "c1")).isTrue();
        repository.delete("u1", "c1");

        verify(redisTemplate).delete(AiRedisKeys.conversation("u1", "c1"));
    }
}
