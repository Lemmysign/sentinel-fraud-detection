package com.sentinel.sentinelcommons.enums;


/**
 * The decision made by a human analyst after
 * reviewing a flagged fraud case.
 *
 * Used by:
 * - case-management-service (analyst sets this)
 * - feedback-service        (reads this to close the loop)
 *
 * Analyst decisions feed back into the model via
 * the feedback service — improving future scoring
 * accuracy over time.
 */
public enum FraudDecision {

    /**
     * Analyst confirms the transaction is fraudulent.
     * Transaction remains BLOCKED.
     * Feeds as a positive fraud signal to the model.
     */
    CONFIRMED_FRAUD,

    /**
     * Analyst determines the transaction is legitimate
     * despite triggering fraud signals. Transaction is
     * unblocked and approved.
     * Feeds as a false positive signal to the model
     * so it learns to avoid similar mistakes.
     */
    FALSE_POSITIVE,

    /**
     * Case has been assigned to an analyst but
     * a decision has not been made yet.
     */
    UNDER_REVIEW
}