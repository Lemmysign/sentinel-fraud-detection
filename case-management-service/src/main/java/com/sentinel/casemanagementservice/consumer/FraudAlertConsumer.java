package com.sentinel.casemanagementservice.consumer;

import com.sentinel.sentinelcommons.event.FraudAlertEvent;
import com.sentinel.casemanagementservice.service.FraudCaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the fraud. Alerts topic.
 *
 * Receives FraudAlertEvent from scoring-service and
 * creates a FraudCase record in PostgreSQL.
 *
 * Idempotent — the service layer checks if a case
 * already exists for this transaction before creating.
 * Safe to retry on failure.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FraudAlertConsumer {

    private final FraudCaseService fraudCaseService;

    @KafkaListener(
            topics = "${sentinel.kafka.topics.fraud-alerts}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            ConsumerRecord<String, FraudAlertEvent> record,
            Acknowledgment ack) {

        FraudAlertEvent event = record.value();

        log.info("FraudAlertEvent received — " +
                        "transaction: {}, case: {}, " +
                        "score: {}, risk: {}",
                event.getTransactionId(),
                event.getCaseId(),
                event.getFraudScore(),
                event.getRiskLevel());

        try {
            fraudCaseService.createCase(event);
            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Failed to create fraud case — " +
                            "transaction: {}, error: {}",
                    event.getTransactionId(),
                    ex.getMessage(), ex);
        }
    }
}
