package com.vincent.orderservice.cache;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public final class RedisTtlJitter {

    private RedisTtlJitter() {
    }

    public static Duration apply(Duration baseTtl, int maxJitterSeconds) {
        if (maxJitterSeconds <= 0) {
            return baseTtl;
        }
        return baseTtl.plusSeconds(ThreadLocalRandom.current().nextInt(maxJitterSeconds + 1));
    }
}
