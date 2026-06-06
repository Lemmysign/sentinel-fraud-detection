package com.sentinel.feedbackservice.model;

import com.sentinel.sentinelcommons.enums.FraudDecision;
import com.sentinel.sentinelcommons.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Internal model representing a processed feedback event.
 *
 * This is what the feedback service works with internally.
 * It mirrors FeedbackEvent from sentinel-commons but is
 * the service's own domain object — decoupled from the
 * Kafka event contract.
 *
 * Why separate from FeedbackEvent?
 * The Kafka event is the transport contract — shared
 * across services. This model is the internal domain
 * representation — owned by this service alone.
 * If the internal model needs extra fields (e.g.
 * processingTimestamp, modelVersion) we add them here
 * without changing the shared event contract.
 *
 * Current use: logging and metrics.
 * Future use: persistence to a time-series database,
 * model retraining triggers, ML feature store updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackRecord {

    private String transactionId;
    private String caseId;
    private String accountId;
    private RiskLevel originalRiskLevel;
    private int originalFraudScore;
    private FraudDecision analystDecision;
    private String analystNotes;
    private LocalDateTime decidedAt;

    /**
     * Whether the AI/rules assessment was correct.
     * true  = model said HIGH/CRITICAL and analyst confirmed fraud
     * false = model said HIGH/CRITICAL but analyst said false positive
     *
     * This is the key signal for model accuracy measurement.
     * Over time: accuracy = truePositives / (truePositives + falsePositives)
     */
    private boolean modelWasCorrect;

    /**
     * When this feedback was processed by this service.
     */
    private LocalDateTime processedAt;
}