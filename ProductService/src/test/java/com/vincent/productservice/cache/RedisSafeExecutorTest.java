package com.vincent.productservice.cache;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSafeExecutorTest {

    @Test
    void optionalReturnsEmptyOnFailure() {
        Optional<String> result = RedisSafeExecutor.optional(() -> {
            throw new RuntimeException("redis down");
        });
        assertThat(result).isEmpty();
    }

    @Test
    void runSwallowsFailure() {
        AtomicBoolean ran = new AtomicBoolean(true);
        RedisSafeExecutor.run(() -> {
            throw new RuntimeException("redis down");
        });
        assertThat(ran).isTrue();
    }
}
