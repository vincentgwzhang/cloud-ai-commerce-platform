package com.vincent.inventoryservice.cache;

import com.vincent.inventoryservice.config.InventoryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryRedisCacheTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private DefaultRedisScript<Long> decrementStockScript;

    private InventoryRedisCache cache;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        InventoryProperties properties = new InventoryProperties(
                Duration.ofMinutes(5),
                Duration.ofHours(1),
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                0,
                List.of("IPHONE17")
        );
        cache = new InventoryRedisCache(redisTemplate, decrementStockScript, properties);
    }

    @Test
    void getAvailableReturnsEmptyOnMiss() {
        when(valueOperations.get("inventory:product:IPHONE17")).thenReturn(null);
        assertThat(cache.getAvailable("IPHONE17")).isEmpty();
    }

    @Test
    void putAndGetAvailable() {
        cache.putAvailable("IPHONE17", 42);
        verify(valueOperations).set(eq("inventory:product:IPHONE17"), eq("42"), any(Duration.class));

        when(valueOperations.get("inventory:product:IPHONE17")).thenReturn("42");
        assertThat(cache.getAvailable("IPHONE17")).contains(42);
    }

    @Test
    void tryAtomicDecrementInterpretsScriptResult() {
        when(redisTemplate.execute(eq(decrementStockScript), eq(List.of("inventory:product:RTX5090")), eq("1")))
                .thenReturn(19L);
        assertThat(cache.tryAtomicDecrement("RTX5090", 1)).contains(19);

        when(redisTemplate.execute(eq(decrementStockScript), eq(List.of("inventory:product:RTX5090")), eq("1")))
                .thenReturn(-1L);
        assertThat(cache.tryAtomicDecrement("RTX5090", 1)).contains(-1);
    }

    @Test
    void incrementAvailableIncrementsKey() {
        cache.incrementAvailable("PS6", 3);
        verify(valueOperations).increment("inventory:product:PS6", 3);
    }

    @Test
    void evictDeletesKey() {
        cache.evict("PS6");
        verify(redisTemplate).delete("inventory:product:PS6");
    }
}
