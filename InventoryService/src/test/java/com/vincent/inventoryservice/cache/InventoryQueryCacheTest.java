package com.vincent.inventoryservice.cache;

import tools.jackson.databind.json.JsonMapper;
import com.vincent.inventoryservice.config.InventoryProperties;
import com.vincent.inventoryservice.dto.InventoryResponse;
import com.vincent.inventoryservice.lock.InventoryDistributedLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryQueryCacheTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private InventoryDistributedLock distributedLock;
    @Mock
    private LocalHotInventoryCache localHotCache;
    @Mock
    private InventoryCacheMetrics cacheMetrics;

    private InventoryQueryCache queryCache;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        InventoryProperties properties = new InventoryProperties(
                Duration.ofMinutes(30),
                Duration.ofHours(24),
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                0,
                List.of("IPHONE17")
        );
        queryCache = new InventoryQueryCache(
                redisTemplate,
                JsonMapper.builder().findAndAddModules().build(),
                distributedLock,
                localHotCache,
                cacheMetrics,
                properties
        );
    }

    @Test
    void returnsFromLocalHotCache() {
        InventoryResponse response = new InventoryResponse("IPHONE17", 10, 1, 1L);
        when(localHotCache.get("IPHONE17")).thenReturn(Optional.of(response));

        assertThat(queryCache.get("IPHONE17", () -> {
            throw new IllegalStateException("should not load");
        })).isEqualTo(response);
        verify(cacheMetrics).recordHit();
    }

    @Test
    void returnsFromRedisDetailCache() throws Exception {
        InventoryResponse response = new InventoryResponse("IPHONE17", 9, 1, 1L);
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        when(localHotCache.get("IPHONE17")).thenReturn(Optional.empty());
        when(valueOperations.get("inventory:product:IPHONE17:detail"))
                .thenReturn(mapper.writeValueAsString(response));

        InventoryResponse result = queryCache.get("IPHONE17", () -> {
            throw new IllegalStateException("should not load");
        });

        assertThat(result.availableStock()).isEqualTo(9);
        verify(cacheMetrics).recordHit();
    }

    @Test
    void loadsWithLockOnMiss() {
        InventoryResponse response = new InventoryResponse("RTX5090", 5, 0, 1L);
        AtomicInteger loads = new AtomicInteger();
        when(localHotCache.get("RTX5090")).thenReturn(Optional.empty());
        when(valueOperations.get("inventory:product:RTX5090:detail")).thenReturn(null);
        when(distributedLock.tryLockKey(anyString())).thenReturn("token");

        InventoryResponse result = queryCache.get("RTX5090", () -> {
            loads.incrementAndGet();
            return response;
        });

        assertThat(result).isEqualTo(response);
        assertThat(loads.get()).isEqualTo(1);
        verify(valueOperations).set(eq("inventory:product:RTX5090:detail"), anyString(), any(Duration.class));
        verify(distributedLock).releaseKey(anyString(), eq("token"));
    }
}
