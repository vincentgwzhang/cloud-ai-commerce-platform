package com.vincent.aiservice.cache;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSafeExecutorTest {

    @Test
    void optionalReturnsValueOrEmptyOnFailure() {
        assertThat(RedisSafeExecutor.optional(() -> "ok")).contains("ok");
        assertThat(RedisSafeExecutor.optional(() -> {
            throw new IllegalStateException("redis down");
        })).isEqualTo(Optional.empty());
    }

    @Test
    void runExecutesAndSwallowsFailure() {
        AtomicBoolean called = new AtomicBoolean();
        RedisSafeExecutor.run(() -> called.set(true));
        RedisSafeExecutor.run(() -> {
            throw new IllegalStateException("redis down");
        });

        assertThat(called).isTrue();
    }
}
