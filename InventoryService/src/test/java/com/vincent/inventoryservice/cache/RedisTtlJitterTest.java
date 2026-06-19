package com.vincent.inventoryservice.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RedisTtlJitterTest {

    @Test
    void returnsBaseTtlWhenJitterDisabled() {
        assertThat(RedisTtlJitter.apply(Duration.ofMinutes(5), 0)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void addsJitterWithinInclusiveRange() {
        Duration ttl = RedisTtlJitter.apply(Duration.ofSeconds(10), 5);

        assertThat(ttl).isBetween(Duration.ofSeconds(10), Duration.ofSeconds(15));
    }
}
