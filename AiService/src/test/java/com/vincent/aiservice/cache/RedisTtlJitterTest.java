package com.vincent.aiservice.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RedisTtlJitterTest {

    @Test
    void returnsBaseTtlWhenJitterDisabled() {
        assertThat(RedisTtlJitter.apply(Duration.ofMinutes(5), 0)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void addsJitterWithinConfiguredRange() {
        Duration result = RedisTtlJitter.apply(Duration.ofMinutes(5), 10);

        assertThat(result).isBetween(Duration.ofMinutes(5), Duration.ofMinutes(5).plusSeconds(10));
    }
}
