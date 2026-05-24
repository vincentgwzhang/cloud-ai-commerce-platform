package com.vincent.inventoryservice.cache;

import tools.jackson.databind.json.JsonMapper;
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
import java.util.List;
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
    @Mock
    private InventoryCacheMetrics cacheMetrics;

    private InventoryIdempotencyStore store;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        jsonMapper = JsonMapper.builder().findAndAddModules().build();
        InventoryProperties properties = new InventoryProperties(
                Duration.ofMinutes(5),
                Duration.ofHours(1),
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                0,
                List.of("IPHONE17")
        );
        store = new InventoryIdempotencyStore(redisTemplate, jsonMapper, cacheMetrics, properties);
    }

    @Test
    void findPreviousResultReturnsDeserializedPayload() throws Exception {
        InventoryResponse response = new InventoryResponse("IPHONE17", 90, 10, 1L);
        when(valueOperations.get("inventory:request:req-1"))
                .thenReturn(jsonMapper.writeValueAsString(response));

        Optional<InventoryResponse> found = store.findPreviousResult("req-1");

        assertThat(found).contains(response);
        verify(cacheMetrics).recordIdempotencyDuplicate();
    }

    @Test
    void tryClaimUsesSetIfAbsent() {
        when(valueOperations.setIfAbsent(eq("inventory:request:req-2"), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(true);
        assertThat(store.tryClaim("req-2")).isTrue();
    }

    @Test
    void findPreviousResultClearsCorruptPayload() {
        when(valueOperations.get("inventory:request:bad")).thenReturn("{not-json");
        assertThat(store.findPreviousResult("bad")).isEmpty();
        verify(redisTemplate).delete("inventory:request:bad");
    }

    @Test
    void saveResultWritesJson() throws Exception {
        InventoryResponse response = new InventoryResponse("PS6", 40, 10, 2L);
        store.saveResult("req-3", response);
        verify(valueOperations).set(
                eq("inventory:request:req-3"),
                eq(jsonMapper.writeValueAsString(response)),
                any(Duration.class)
        );
    }
}
