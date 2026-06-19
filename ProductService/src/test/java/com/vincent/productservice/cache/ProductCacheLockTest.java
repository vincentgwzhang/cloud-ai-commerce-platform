package com.vincent.productservice.cache;

import com.vincent.productservice.config.ProductCacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCacheLockTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private ProductCacheLock lock;

    @BeforeEach
    void setUp() {
        lock = new ProductCacheLock(redisTemplate, new ProductCacheProperties(
                Duration.ofMinutes(5), Duration.ofMinutes(5), Duration.ofSeconds(30),
                Duration.ofSeconds(3), Duration.ofMinutes(1), 0, List.of()
        ));
    }

    @Test
    void tryAcquireReturnsTokenWhenRedisSetNxSucceeds() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("lock:P1"), anyString(), eq(Duration.ofSeconds(3)))).thenReturn(true);

        String token = lock.tryAcquire("lock:P1");

        assertThat(token).isNotBlank();
    }

    @Test
    void tryAcquireReturnsNullWhenLockAlreadyExists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("lock:P1"), anyString(), eq(Duration.ofSeconds(3)))).thenReturn(false);

        assertThat(lock.tryAcquire("lock:P1")).isNull();
    }

    @Test
    void releaseDeletesOnlyWhenTokenStillOwnsLock() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("lock:P1")).thenReturn("token");

        lock.release("lock:P1", "token");
        lock.release("lock:P1", "other");

        verify(redisTemplate).delete("lock:P1");
    }

    @Test
    void releaseIgnoresNullToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("lock:P1")).thenReturn("token");

        lock.release("lock:P1", null);

        verify(redisTemplate, never()).delete("lock:P1");
    }
}
