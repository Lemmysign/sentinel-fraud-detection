package com.sentinel.sentinelcommons.enums;

/**
 * Fraud risk level assigned by the scoring service
 * based on the AI model's output score (0-100).
 *
 * Used by:
 * - scoring-service  (assigns the level)
 * - rules-engine     (contributes to the score)
 * - case-management  (filters cases by risk)
 */
public enum RiskLevel {

    /**
     * Score 0-30.
     * Transaction shows no significant fraud signals.
     * Approve automatically.
     */
    LOW,

    /**
     * Score 31-60.
     * Some anomalies detected but not conclusive.
     * Apply additional checks before approving.
     */
    MEDIUM,

    /**
     * Score 61-85.
     * Strong fraud signals present.
     * Flag for human review.
     */
    HIGH,

    /**
     * Score 86-100.
     * Transaction is almost certainly fraudulent.
     * Block immediately and create a priority case.
     */
    CRITICAL
}