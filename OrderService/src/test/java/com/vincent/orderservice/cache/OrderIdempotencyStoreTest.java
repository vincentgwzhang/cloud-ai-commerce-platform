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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderIdempotencyStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private OrderCacheMetrics cacheMetrics;

    private OrderIdempotencyStore store;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        jsonMapper = JsonMapper.builder().findAndAddModules().build();
        OrderCacheProperties properties = new OrderCacheProperties(
                Duration.ofHours(1), Duration.ofMinutes(5), 0
        );
        store = new OrderIdempotencyStore(redisTemplate, jsonMapper, cacheMetrics, properties);
    }

    @Test
    void tryClaimUsesSetIfAbsent() {
        when(valueOperations.setIfAbsent(eq("order:request:req-1"), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(true);
        assertThat(store.tryClaim("req-1")).isTrue();
    }

    @Test
    void findPreviousResultReturnsPayload() throws Exception {
        OrderResponse response = new OrderResponse(
                "ORD-1", "IPHONE17", 1, new BigDecimal("999"),
                OrderStatus.CREATED, "req-2", Instant.now(), Instant.now()
        );
        when(valueOperations.get("order:request:req-2")).thenReturn(jsonMapper.writeValueAsString(response));
        Optional<OrderResponse> found = store.findPreviousResult("req-2");
        assertThat(found).contains(response);
        verify(cacheMetrics).recordIdempotencyDuplicate();
    }

    @Test
    void saveResultWritesJson() throws Exception {
        OrderResponse response = new OrderResponse(
                "ORD-1", "IPHONE17", 1, new BigDecimal("999"),
                OrderStatus.CREATED, "req-3", Instant.now(), Instant.now()
        );
        store.saveResult("req-3", response);
        verify(valueOperations).set(eq("order:request:req-3"), any(String.class), any(Duration.class));
    }

    @Test
    void findPreviousResultClearsCorruptJson() {
        when(valueOperations.get("order:request:bad")).thenReturn("{bad");
        assertThat(store.findPreviousResult("bad")).isEmpty();
        verify(redisTemplate).delete("order:request:bad");
    }
}
