package com.sentinel.rulesengineservice.consumer;

import com.sentinel.sentinelcommons.event.TransactionEvent;
import com.sentinel.rulesengineservice.service.RulesEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the transactions.raw topic.
 *
 * Entry point of the rules-engine-service.
 * Every transaction published by ingestion-service
 * arrives here first.
 *
 * Design principle — keep consumers thin:
 * This class has one job: receive the Kafka message
 * and hand it to the service layer.
 * All business logic lives in RulesEngine.
 * All Kafka concerns live in KafkaConsumerConfig.
 * This class is just the bridge between them.
 *
 * Manual acknowledgement pattern:
 * ack.acknowledge() is called ONLY after successful
 * processing. On exception, acknowledge is never called
 * — Kafka redelivers the message on restart.
 * This guarantees at-least-once processing semantics.
 *
 * Why ConsumerRecord instead of just TransactionEvent?
 * ConsumerRecord gives us partition and offset metadata
 * for logging. This is invaluable during debugging —
 * you can trace exactly which Kafka message caused
 * an issue and replay from that offset.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionConsumer {

    private final RulesEngineService rulesEngineService;

    @KafkaListener(
            topics = "${sentinel.kafka.topics.transactions-raw}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            ConsumerRecord<String, TransactionEvent> record,
            Acknowledgment ack) {

        TransactionEvent transaction = record.value();

        log.info("Transaction received for evaluation — " +
                        "id: {}, account: {}, partition: {}, offset: {}",
                transaction.getTransactionId(),
                transaction.getAccountId(),
                record.partition(),
                record.offset());

        try {
            rulesEngineService.evaluateAndPublish(transaction);

            // Acknowledge ONLY after successful processing
            ack.acknowledge();

            log.debug("Transaction acknowledged — id: {}",
                    transaction.getTransactionId());

        } catch (Exception ex) {
            /*
             * Do NOT acknowledge on failure.
             * Message will be redelivered on service restart.
             *
             * Production enhancement:
             * After N retries, move to a dead letter topic
             * (transactions.raw.DLT) for manual inspection.
             * Prevents poison pill messages from blocking
             * the entire partition indefinitely.
             */
            log.error("Rules evaluation failed — " +
                            "id: {}, partition: {}, offset: {}, error: {}",
                    transaction.getTransactionId(),
                    record.partition(),
                    record.offset(),
                    ex.getMessage(), ex);
        }
    }
}