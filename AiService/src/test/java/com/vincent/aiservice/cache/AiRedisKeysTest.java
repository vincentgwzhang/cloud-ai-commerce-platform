package com.vincent.aiservice.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiRedisKeysTest {

    @Test
    void buildsStableKeys() {
        assertThat(AiRedisKeys.conversation("u1", "c1")).isEqualTo("ai:chat:conversation:u1:c1");
        assertThat(AiRedisKeys.recommendation("u1")).isEqualTo("ai:reco:u1");
        assertThat(AiRedisKeys.recommendationLock("u1")).isEqualTo("ai:reco:lock:u1");
        assertThat(AiRedisKeys.dedup("evt")).isEqualTo("ai:dedup:evt");
    }
}
