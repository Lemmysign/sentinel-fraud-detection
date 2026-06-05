package com.sentinel.rulesengineservice.config;

import com.sentinel.sentinelcommons.event.FraudScoredEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer config for publishing FraudScoredEvent
 * to the fraud.scored topic.
 *
 * Same production settings as ingestion-service:
 * idempotence, batching, compression, retries.
 * Consistent producer config across all services
 * ensures uniform reliability guarantees throughout
 * the pipeline.
 */

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, FraudScoredEvent>
    producerFactory() {

        Map<String, Object> config = new HashMap<>();

        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JsonSerializer.class);

        // Exactly-once delivery guarantee
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,
                true);

        // Wait for all replicas to acknowledge
        config.put(ProducerConfig.ACKS_CONFIG, "all");

        // Retry indefinitely on transient failures
        config.put(ProducerConfig.RETRIES_CONFIG,
                Integer.MAX_VALUE);

        // 16KB batch size — good throughput/latency balance
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);

        // 5ms linger — allows batching under load
        config.put(ProducerConfig.LINGER_MS_CONFIG, 5);

        // Snappy compression — fast, reasonable ratio
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG,
                "snappy");

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, FraudScoredEvent>
    kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}

