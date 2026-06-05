package com.sentinel.casemanagementservice.config;

import com.sentinel.sentinelcommons.event.FraudAlertEvent;
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
 * Kafka consumer config for case-management-service.
 * Consumes FraudAlertEvent from fraud.alerts topic.
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, FraudAlertEvent>
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
            FraudAlertEvent> kafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String,
                FraudAlertEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(2);
        factory.getContainerProperties().setAckMode(
                ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}