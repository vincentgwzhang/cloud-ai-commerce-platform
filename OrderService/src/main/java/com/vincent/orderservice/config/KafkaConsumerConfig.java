package com.vincent.orderservice.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Consumer retry + DLQ (Dead Letter Queue).
 *
 * <p>Transient failures (DB timeout, broker hiccup) may succeed on retry.
 * After max attempts, the message goes to {@code order-dlq} for manual inspection —
 * without DLQ, poison messages block the partition forever.
 *
 * <p>Delivery is at-least-once, not exactly-once; idempotent handlers are required.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            OrderKafkaProperties orderKafkaProperties
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception ex) ->
                        new TopicPartition(orderKafkaProperties.topics().orderDlq(), record.partition())
        );
        FixedBackOff backOff = new FixedBackOff(
                orderKafkaProperties.retryIntervalMs(),
                orderKafkaProperties.maxRetries()
        );
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(DeserializationException.class);
        handler.setRetryListeners((record, ex, attempt) -> log.warn(
                "Kafka consumer retry attempt={} topic={} partition={} offset={} error={}",
                attempt, record.topic(), record.partition(), record.offset(), ex.getMessage()));
        handler.setCommitRecovered(true);
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> orderKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
