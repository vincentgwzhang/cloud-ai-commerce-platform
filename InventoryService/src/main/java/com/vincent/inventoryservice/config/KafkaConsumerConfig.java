package com.vincent.inventoryservice.config;

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
import org.springframework.util.backoff.FixedBackOff;

/**
 * Consumer retry + DLQ for inventory Kafka listeners.
 *
 * <p>Transient failures (DB lock timeout, Redis blip) may succeed on retry.
 * After max attempts, poison messages land on {@code inventory-dlq} for manual inspection —
 * without a DLQ they would block the partition indefinitely.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler inventoryKafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            InventoryKafkaProperties kafkaProperties
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception ex) ->
                        new TopicPartition(kafkaProperties.topics().inventoryDlq(), record.partition())
        );
        FixedBackOff backOff = new FixedBackOff(
                kafkaProperties.retryIntervalMs(),
                kafkaProperties.maxRetries()
        );
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(DeserializationException.class);
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> inventoryKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler inventoryKafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(inventoryKafkaErrorHandler);
        return factory;
    }
}
