package com.vincent.inventoryservice.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSafeExecutorTest {

    @Test
    void optionalReturnsValueWhenRedisOperationSucceeds() {
        assertThat(RedisSafeExecutor.optional(() -> "value")).contains("value");
    }

    @Test
    void optionalReturnsEmptyWhenRedisOperationFails() {
        assertThat(RedisSafeExecutor.optional(() -> {
            throw new IllegalStateException("redis down");
        })).isEmpty();
    }

    @Test
    void runExecutesAndSwallowsRedisFailures() {
        final boolean[] executed = {false};

        RedisSafeExecutor.run(() -> executed[0] = true);
        RedisSafeExecutor.run(() -> {
            throw new IllegalStateException("redis down");
        });

        assertThat(executed[0]).isTrue();
    }
}
