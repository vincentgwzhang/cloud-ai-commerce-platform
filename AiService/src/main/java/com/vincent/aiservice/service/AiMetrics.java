package com.vincent.aiservice.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AiMetrics {

    private final Counter recommendationRequests;
    private final Counter recommendationCacheHit;
    private final Counter recommendationCacheMiss;
    private final Counter chatRequests;
    private final Counter chatFailures;
    private final Timer chatLatency;
    private final Counter kafkaEventConsumed;
    private final Counter kafkaConsumeFailure;
    private final Counter ragAskRequests;
    private final Counter ragAskFailures;
    private final Timer ragAskLatency;
    private final MeterRegistry meterRegistry;

    public AiMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.recommendationRequests = meterRegistry.counter("ai_recommendation_requests_total");
        this.recommendationCacheHit = meterRegistry.counter("ai_recommendation_cache_hit_total");
        this.recommendationCacheMiss = meterRegistry.counter("ai_recommendation_cache_miss_total");
        this.chatRequests = meterRegistry.counter("ai_chat_requests_total");
        this.chatFailures = meterRegistry.counter("ai_chat_failures_total");
        this.chatLatency = meterRegistry.timer("ai_chat_latency");
        this.kafkaEventConsumed = meterRegistry.counter("ai_kafka_event_consumed_total");
        this.kafkaConsumeFailure = meterRegistry.counter("ai_kafka_consume_failure_total");
        this.ragAskRequests = meterRegistry.counter("ai_rag_ask_requests_total");
        this.ragAskFailures = meterRegistry.counter("ai_rag_ask_failures_total");
        this.ragAskLatency = meterRegistry.timer("ai_rag_ask_latency");
    }

    public void recordRecommendationRequest() {
        recommendationRequests.increment();
    }

    public void recordRecommendationCacheHit() {
        recommendationCacheHit.increment();
    }

    public void recordRecommendationCacheMiss() {
        recommendationCacheMiss.increment();
    }

    public void recordChatRequest() {
        chatRequests.increment();
    }

    public void recordChatFailure() {
        chatFailures.increment();
    }

    public void recordChatLatency(long millis) {
        chatLatency.record(Duration.ofMillis(millis));
    }

    public void recordKafkaEventConsumed() {
        kafkaEventConsumed.increment();
    }

    public void recordKafkaConsumeFailure() {
        kafkaConsumeFailure.increment();
    }

    public void recordRagAskRequest() {
        ragAskRequests.increment();
    }

    public void recordRagAskFailure() {
        ragAskFailures.increment();
    }

    public void recordRagAskLatency(long millis) {
        ragAskLatency.record(Duration.ofMillis(millis));
    }

    public void recordProductSync(String operation) {
        meterRegistry.counter("ai_product_sync_total", "operation", operation).increment();
    }
}
