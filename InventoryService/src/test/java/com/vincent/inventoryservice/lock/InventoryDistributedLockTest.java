package com.vincent.inventoryservice.lock;

import com.vincent.inventoryservice.config.InventoryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryDistributedLockTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private InventoryDistributedLock lock;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        InventoryProperties properties = new InventoryProperties(
                "inv:",
                Duration.ofMinutes(5),
                "id:",
                Duration.ofHours(1),
                "test:lock:",
                Duration.ofSeconds(10)
        );
        lock = new InventoryDistributedLock(redisTemplate, properties);
    }

    @Test
    void tryLockReturnsTokenWhenAcquired() {
        when(valueOperations.setIfAbsent(eq("test:lock:IPHONE17"), any(String.class), any(Duration.class)))
                .thenReturn(true);
        assertThat(lock.tryLock("IPHONE17")).isNotBlank();
    }

    @Test
    void tryLockReturnsNullWhenBusy() {
        when(valueOperations.setIfAbsent(eq("test:lock:IPHONE17"), any(String.class), any(Duration.class)))
                .thenReturn(false);
        assertThat(lock.tryLock("IPHONE17")).isNull();
    }

    @Test
    void releaseDeletesMatchingToken() {
        when(valueOperations.get("test:lock:IPHONE17")).thenReturn("token-a");
        lock.release("IPHONE17", "token-a");
        verify(redisTemplate).delete("test:lock:IPHONE17");
    }

    @Test
    void releaseSkipsWhenTokenMismatch() {
        when(valueOperations.get("test:lock:IPHONE17")).thenReturn("token-a");
        lock.release("IPHONE17", "token-b");
        verify(redisTemplate, never()).delete("test:lock:IPHONE17");
    }
}
