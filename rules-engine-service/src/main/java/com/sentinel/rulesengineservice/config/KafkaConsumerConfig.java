package com.sentinel.rulesengineservice.config;

import com.sentinel.sentinelcommons.event.TransactionEvent;
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
 * Kafka consumer configuration for rules-engine-service.
 *
 * Three production decisions made here:
 *
 * 1. MANUAL ACKNOWLEDGEMENT
 * The consumer commits offsets only after successful
 * processing. If the service crashes mid-evaluation,
 * the transaction is redelivered on restart.
 * No transaction is silently dropped.
 *
 * 2. CONCURRENCY = 3
 * Three threads consume from transactions.raw in parallel.
 * Each thread handles one partition. Transactions on
 * different partitions are evaluated simultaneously.
 * Transactions on the SAME partition maintain order
 * (Kafka guarantee — same key → same partition).
 *
 * In production, concurrency should equal the number
 * of partitions on the transactions.raw topic.
 *
 * 3. MAX POLL RECORDS = 10
 * Limits how many records are fetched per poll cycle.
 * Prevents the rules engine from being overwhelmed
 * during catch-up after downtime.
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, TransactionEvent>
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

        // Manual commit — we control when offset advances
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false);

        // Process up to 10 records per poll
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
                10);

        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String,
            TransactionEvent> kafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String,
                TransactionEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());

        // 3 concurrent consumer threads
        factory.setConcurrency(3);

        // Manual acknowledgement mode
        factory.getContainerProperties().setAckMode(
                ContainerProperties.AckMode.MANUAL);

        return factory;
    }
}