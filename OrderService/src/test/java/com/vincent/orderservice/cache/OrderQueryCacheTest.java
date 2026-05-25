package com.vincent.orderservice.cache;

import tools.jackson.databind.json.JsonMapper;
import com.vincent.orderservice.config.OrderCacheProperties;
import com.vincent.orderservice.dto.OrderResponse;
import com.vincent.orderservice.entity.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderQueryCacheTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private OrderQueryCache cache;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        jsonMapper = JsonMapper.builder().findAndAddModules().build();
        OrderCacheProperties properties = new OrderCacheProperties(
                Duration.ofHours(1), Duration.ofMinutes(5), 0
        );
        cache = new OrderQueryCache(redisTemplate, jsonMapper, properties);
    }

    @Test
    void putAndGet() throws Exception {
        OrderResponse response = new OrderResponse(
                "ORD-1", "IPHONE17", 1, new BigDecimal("999"),
                OrderStatus.CREATED, "req", Instant.now(), Instant.now()
        );
        cache.put(response);
        verify(valueOperations).set(eq("order:detail:ORD-1"), any(String.class), any(Duration.class));

        when(valueOperations.get("order:detail:ORD-1")).thenReturn(jsonMapper.writeValueAsString(response));
        assertThat(cache.get("ORD-1")).contains(response);
    }

    @Test
    void evictDeletesKey() {
        cache.evict("ORD-1");
        verify(redisTemplate).delete("order:detail:ORD-1");
    }
}
