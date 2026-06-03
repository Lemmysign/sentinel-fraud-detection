package com.sentinel.sentinelcommons.enums;

/**
 * Tracks a transaction's position in the fraud
 * detection pipeline.
 *
 * State transitions:
 * RECEIVED → PROCESSING → APPROVED
 *                      → FLAGGED → (analyst reviews)
 *                      → BLOCKED
 */
public enum TransactionStatus {

    /**
     * Transaction has been received and validated
     * by the ingestion service. Published to Kafka
     * and awaiting rules evaluation.
     */
    RECEIVED,

    /**
     * Transaction is currently being evaluated
     * by the rules engine and scoring service.
     */
    PROCESSING,

    /**
     * Transaction passed all fraud checks.
     * Safe to proceed.
     */
    APPROVED,

    /**
     * Transaction is suspicious but not definitively
     * fraudulent. Routed to a human analyst for review.
     */
    FLAGGED,

    /**
     * Transaction has been identified as fraudulent
     * and rejected. Either by the AI model with high
     * confidence or confirmed by a human analyst.
     */
    BLOCKED
}