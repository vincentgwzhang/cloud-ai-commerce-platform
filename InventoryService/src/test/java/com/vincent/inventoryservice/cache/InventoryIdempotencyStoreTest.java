package com.vincent.inventoryservice.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vincent.inventoryservice.config.InventoryProperties;
import com.vincent.inventoryservice.dto.InventoryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryIdempotencyStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private InventoryIdempotencyStore store;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        InventoryProperties properties = new InventoryProperties(
                "inv:",
                Duration.ofMinutes(5),
                "test:id:",
                Duration.ofHours(1),
                "lock:",
                Duration.ofSeconds(10)
        );
        store = new InventoryIdempotencyStore(redisTemplate, objectMapper, properties);
    }

    @Test
    void findPreviousResultReturnsDeserializedPayload() throws Exception {
        InventoryResponse response = new InventoryResponse("IPHONE17", 90, 10, 1L);
        when(valueOperations.get("test:id:req-1"))
                .thenReturn(objectMapper.writeValueAsString(response));

        Optional<InventoryResponse> found = store.findPreviousResult("req-1");

        assertThat(found).contains(response);
    }

    @Test
    void tryClaimUsesSetIfAbsent() {
        when(valueOperations.setIfAbsent(eq("test:id:req-2"), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(true);
        assertThat(store.tryClaim("req-2")).isTrue();
    }

    @Test
    void findPreviousResultClearsCorruptPayload() {
        when(valueOperations.get("test:id:bad")).thenReturn("{not-json");
        assertThat(store.findPreviousResult("bad")).isEmpty();
        verify(redisTemplate).delete("test:id:bad");
    }

    @Test
    void saveResultWritesJson() throws Exception {
        InventoryResponse response = new InventoryResponse("PS6", 40, 10, 2L);
        store.saveResult("req-3", response);
        verify(valueOperations).set(
                eq("test:id:req-3"),
                eq(objectMapper.writeValueAsString(response)),
                any(Duration.class)
        );
    }
}
