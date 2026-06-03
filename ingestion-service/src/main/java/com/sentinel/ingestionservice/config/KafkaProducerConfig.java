package com.sentinel.ingestionservice.config;

import com.sentinel.sentinelcommons.event.TransactionEvent;
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
 * Kafka producer configuration.
 *
 * Why configure manually instead of relying on
 * Spring Boot auto-configuration?
 *
 * Auto-configuration works for simple cases but
 * production Kafka producers need tuning:
 * - Batching for throughput
 * - Compression for network efficiency
 * - Idempotence to prevent duplicate messages
 * - Acknowledgement settings for reliability
 *
 * These settings directly impact performance and
 * reliability under load — important for a system
 * that needs to handle thousands of transactions
 * per second.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Configures the Kafka producer with production-grade
     * settings optimised for throughput and reliability.
     */
    @Bean
    public ProducerFactory<String, TransactionEvent> producerFactory() {
        Map<String, Object> config = new HashMap<>();

        // Connection
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers);

        // Serializers
        // Key is the transaction ID — a String
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        // Value is the TransactionEvent — serialized to JSON
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JsonSerializer.class);

        /*
         * IDEMPOTENT PRODUCER
         * Guarantees exactly-once delivery to Kafka.
         * Without this, a network failure between send
         * and acknowledgement could cause the same
         * transaction to be published twice — downstream
         * services would process it twice.
         * In a fraud system, processing the same transaction
         * twice could incorrectly inflate velocity counts.
         */
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        /*
         * ACKNOWLEDGEMENT = ALL
         * Kafka waits for ALL in-sync replicas to confirm
         * the message before acknowledging.
         * Required for idempotence. Prevents message loss
         * if the broker fails immediately after receiving.
         * In a single-broker local setup this still works —
         * the one broker acknowledges itself.
         */
        config.put(ProducerConfig.ACKS_CONFIG, "all");

        /*
         * RETRIES
         * How many times to retry a failed publish.
         * Combined with idempotence, retries are safe —
         * Kafka deduplicates them automatically.
         * Integer.MAX_VALUE effectively means retry forever
         * until success or the timeout expires.
         */
        config.put(ProducerConfig.RETRIES_CONFIG,
                Integer.MAX_VALUE);

        /*
         * BATCH SIZE — 16KB
         * Kafka batches messages going to the same partition
         * and sends them together. Larger batches = better
         * throughput but slightly higher latency.
         * 16KB is a good balance for transaction volumes.
         */
        config.put(ProducerConfig.BATCH_SIZE_CONFIG,
                16384);

        /*
         * LINGER MS — 5ms
         * How long to wait for more messages to fill a batch
         * before sending. 0 = send immediately (low latency,
         * poor throughput). 5ms = small delay allows batching
         * which significantly improves throughput under load.
         */
        config.put(ProducerConfig.LINGER_MS_CONFIG, 5);

        /*
         * COMPRESSION — SNAPPY
         * Compresses message batches before sending.
         * Snappy is fast with reasonable compression ratio —
         * reduces network bandwidth without significant CPU cost.
         * Other options: gzip (better compression, more CPU),
         * lz4 (fastest), zstd (best ratio, more CPU).
         */
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG,
                "snappy");

        /*
         * REQUEST TIMEOUT — 30 seconds
         * How long to wait for Kafka to acknowledge a send.
         * After this, the send is considered failed and
         * the retry mechanism kicks in.
         */
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
                30000);

        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * KafkaTemplate is the Spring abstraction over the
     * raw Kafka producer. Injected into TransactionServiceImpl
     * and used to publish events.
     *
     * The generic types <String, TransactionEvent> ensure
     * compile-time type safety — you cannot accidentally
     * publish the wrong event type to this template.
     */
    @Bean
    public KafkaTemplate<String, TransactionEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}