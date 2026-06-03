package com.sentinel.sentinelcommons.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sentinel.sentinelcommons.enums.RiskLevel;
import com.sentinel.sentinelcommons.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Kafka event published by scoring-service to the
 * fraud.scored topic after AI risk assessment.
 *
 * Consumed by:
 * - case-management-service (creates cases for HIGH/CRITICAL)
 *
 * Contains the original transaction details plus the
 * fraud assessment results from both the rules engine
 * and the AI scoring model.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudScoredEvent {

    /**
     * Links back to the original TransactionEvent.
     */
    private String transactionId;

    private String accountId;
    private String merchantId;

    /**
     * Fraud risk score from 0 to 100.
     * 0  = definitely legitimate
     * 100 = definitely fraudulent
     */
    private int fraudScore;

    /**
     * Risk level derived from fraudScore.
     * LOW / MEDIUM / HIGH / CRITICAL
     */
    private RiskLevel riskLevel;

    /**
     * Updated transaction status after scoring.
     * APPROVED / FLAGGED / BLOCKED
     */
    private TransactionStatus status;

    /**
     * Human-readable explanation from the AI model.
     * Example: "Unusual transaction amount for this
     * account. New device detected. High velocity
     * in last 30 seconds."
     *
     * Stored on the fraud case for analyst review.
     */
    private String aiExplanation;

    /**
     * List of specific rules that fired during
     * rules engine evaluation.
     * Example: ["VELOCITY_CHECK_FAILED",
     *           "NEW_DEVICE_DETECTED",
     *           "AMOUNT_THRESHOLD_EXCEEDED"]
     */
    private List<String> triggeredRules;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime scoredAt;
}