package com.vincent.inventoryservice.cache;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/** Spreads key expirations to mitigate cache avalanche. */
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
