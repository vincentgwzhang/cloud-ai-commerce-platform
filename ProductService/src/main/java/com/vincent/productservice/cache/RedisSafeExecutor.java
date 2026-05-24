package com.vincent.productservice.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Redis is auxiliary — on temporary failure we degrade to DB instead of failing the request.
 */
public final class RedisSafeExecutor {

    private static final Logger log = LoggerFactory.getLogger(RedisSafeExecutor.class);

    private RedisSafeExecutor() {
    }

    public static <T> Optional<T> optional(Supplier<T> redisOperation) {
        try {
            return Optional.ofNullable(redisOperation.get());
        } catch (Exception ex) {
            log.warn("Redis unavailable, degrading: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public static void run(Runnable redisOperation) {
        try {
            redisOperation.run();
        } catch (Exception ex) {
            log.warn("Redis write skipped (degraded): {}", ex.getMessage());
        }
    }
}
