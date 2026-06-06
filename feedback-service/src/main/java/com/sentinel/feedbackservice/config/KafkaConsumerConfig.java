package com.sentinel.feedbackservice.config;

import com.sentinel.sentinelcommons.event.FeedbackEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer config for feedback-service.
 *
 * Concurrency = 1.
 * Feedback processing is not latency-sensitive —
 * there is no SLA on how quickly feedback is processed.
 * A single consumer is sufficient and avoids any
 * ordering complexity.
 *
 * Manual acknowledgement — same pattern as all other
 * consumers. Only acknowledge after successful processing.
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, FeedbackEvent>
    consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG,
                groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES,
                "com.sentinel.*");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false);
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
                10);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String,
            FeedbackEvent> kafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String,
                FeedbackEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(1);
        factory.getContainerProperties().setAckMode(
                ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}