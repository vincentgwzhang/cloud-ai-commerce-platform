package com.vincent.productservice.cache;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Random TTL jitter spreads expirations to reduce cache avalanche / synchronized expiry spikes.
 */
public final class RedisTtlJitter {

    private RedisTtlJitter() {
    }

    public static Duration apply(Duration baseTtl, int maxJitterSeconds) {
        if (maxJitterSeconds <= 0) {
            return baseTtl;
        }
        int jitter = ThreadLocalRandom.current().nextInt(maxJitterSeconds + 1);
        return baseTtl.plusSeconds(jitter);
    }
}
