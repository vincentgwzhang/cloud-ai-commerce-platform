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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    /**
     * 
     * Spring Kafka Consumer 的统一异常处理策略
     * 
     * 
     */
    @Bean
    public DefaultErrorHandler inventoryKafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            InventoryKafkaProperties kafkaProperties
    ) {

        /**
         * 
         * Step 1: 重试彻底失败以后，把消息送到死信队列
         * 
         */
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,

                /**
                 * 原来在哪个partition，DLQ也放到同一个partition
                 */
                (ConsumerRecord<?, ?> record, Exception ex) ->
                        new TopicPartition(kafkaProperties.topics().inventoryDlq(), record.partition())
        );


        /**
         * 
         * Step 2: 失败后固定等待1秒，最多重试3次
         * 
         */
        FixedBackOff backOff = new FixedBackOff(
                kafkaProperties.retryIntervalMs(),
                kafkaProperties.maxRetries()
        );


        /**
         * 
         * Step 3: Config in total
         * 
         */
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

        /**
         * 
         * Step 3.1: 如果是遇到 DeserializationException.class， 不用尝试，直接放入DLQ
         * 
         */
        handler.addNotRetryableExceptions(DeserializationException.class);


        /**
         * 
         * Step 3.2： 遇到错误的时候怎么做，可以打印日志
         * 
         */
        handler.setRetryListeners((record, ex, attempt) -> log.warn(
                "Kafka consumer retry attempt={} topic={} partition={} offset={} error={}",
                attempt, record.topic(), record.partition(), record.offset(), ex.getMessage()));

        /**
         * 
         * Step 3.3: 假如遇到要放入私信队列的情况，那么也依然代表 “原消息已经处理完了， 提交offset”
         * 
         */
        handler.setCommitRecovered(true);
        
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
