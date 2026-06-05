package com.sentinel.scoringservice.consumer;

import com.sentinel.sentinelcommons.event.FraudScoredEvent;
import com.sentinel.scoringservice.service.ScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the fraud.scored topic.
 *
 * Consumes FraudScoredEvent published by
 * rules-engine-service, triggers AI scoring,
 * and publishes FraudAlertEvent for high-risk cases.
 *
 * Manual acknowledgement — same pattern as rules engine.
 * Only acknowledge after successful AI scoring and
 * Kafka publish. Failed transactions are redelivered.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FraudScoredEventConsumer {

    private final ScoringService scoringService;

    @KafkaListener(
            topics = "${sentinel.kafka.topics.fraud-scored}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            ConsumerRecord<String, FraudScoredEvent> record,
            Acknowledgment ack) {

        FraudScoredEvent event = record.value();

        log.info("FraudScoredEvent received — " +
                        "transaction: {}, rules score: {}, " +
                        "risk: {}, partition: {}, offset: {}",
                event.getTransactionId(),
                event.getFraudScore(),
                event.getRiskLevel(),
                record.partition(),
                record.offset());

        try {
            scoringService.scoreAndPublish(event);
            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Scoring failed — transaction: {}, " +
                            "partition: {}, offset: {}, error: {}",
                    event.getTransactionId(),
                    record.partition(),
                    record.offset(),
                    ex.getMessage(), ex);
            // Do not acknowledge — message redelivered on restart
        }
    }
}