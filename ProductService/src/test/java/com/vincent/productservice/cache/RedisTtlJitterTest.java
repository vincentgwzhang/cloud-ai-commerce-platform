package com.vincent.productservice.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RedisTtlJitterTest {

    @Test
    void appliesJitterWithinRange() {
        Duration base = Duration.ofMinutes(10);
        Duration withJitter = RedisTtlJitter.apply(base, 60);
        assertThat(withJitter).isBetween(base, base.plusSeconds(60));
    }

    @Test
    void returnsBaseWhenJitterDisabled() {
        Duration base = Duration.ofMinutes(10);
        assertThat(RedisTtlJitter.apply(base, 0)).isEqualTo(base);
    }
}
