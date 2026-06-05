package com.sentinel.rulesengineservice.service.impl;

import com.sentinel.sentinelcommons.event.FraudScoredEvent;
import com.sentinel.sentinelcommons.event.TransactionEvent;
import com.sentinel.rulesengineservice.engine.RulesEngine;
import com.sentinel.rulesengineservice.service.RulesEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Coordinates rules evaluation and Kafka publishing.
 *
 * This class is deliberately thin — it delegates
 * evaluation to RulesEngine and publishing to
 * KafkaTemplate. It is the seam between business
 * logic and infrastructure.
 *
 * Why keep service thin here?
 * The rules engine is independently testable.
 * The Kafka publishing is independently testable.
 * This class just wires them together — there is
 * no logic here to test beyond the wiring.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RulesEngineServiceImpl implements RulesEngineService {

    private final RulesEngine rulesEngine;
    private final KafkaTemplate<String, FraudScoredEvent> kafkaTemplate;

    @Value("${sentinel.kafka.topics.fraud-scored}")
    private String fraudScoredTopic;

    @Override
    public void evaluateAndPublish(TransactionEvent transaction) {

        // Evaluate all rules — returns fully scored event
        FraudScoredEvent scoredEvent =
                rulesEngine.evaluate(transaction);

        // Publish to fraud.scored topic
        // transactionId as key preserves ordering —
        // all events for one transaction go to same partition
        kafkaTemplate.send(
                fraudScoredTopic,
                scoredEvent.getTransactionId(),
                scoredEvent
        ).whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("FraudScoredEvent published — " +
                                "transaction: {}, score: {}, " +
                                "risk: {}, status: {}",
                        scoredEvent.getTransactionId(),
                        scoredEvent.getFraudScore(),
                        scoredEvent.getRiskLevel(),
                        scoredEvent.getStatus());
            } else {
                log.error("Failed to publish FraudScoredEvent — " +
                                "transaction: {}, error: {}",
                        scoredEvent.getTransactionId(),
                        ex.getMessage(), ex);
            }
        });
    }
}