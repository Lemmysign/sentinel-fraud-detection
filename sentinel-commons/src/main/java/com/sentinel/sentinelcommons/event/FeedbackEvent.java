package com.sentinel.sentinelcommons.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sentinel.sentinelcommons.enums.FraudDecision;
import com.sentinel.sentinelcommons.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Kafka event published by case-management-service
 * to the model.feedback topic when an analyst makes
 * a decision on a fraud case.
 *
 * Consumed by:
 * - feedback-service (closes the learning loop)
 *
 * This event is the foundation of continuous model
 * improvement. Every analyst decision teaches the
 * system what real fraud looks like vs false positives.
 *
 * In a mature system this feeds a retraining pipeline.
 * In Sentinel it is logged and stored for future
 * model improvement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackEvent {

    private String transactionId;
    private String caseId;
    private String accountId;

    /**
     * The original AI risk level for this transaction.
     * Compared with the analyst's decision to measure
     * model accuracy over time.
     */
    private RiskLevel originalRiskLevel;

    /**
     * The original fraud score (0-100).
     */
    private int originalFraudScore;

    /**
     * What the analyst actually decided.
     * CONFIRMED_FRAUD or FALSE_POSITIVE tells us
     * whether the model was right or wrong.
     */
    private FraudDecision analystDecision;

    /**
     * Optional notes from the analyst explaining
     * their decision. Valuable training signal.
     */
    private String analystNotes;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime decidedAt;
}