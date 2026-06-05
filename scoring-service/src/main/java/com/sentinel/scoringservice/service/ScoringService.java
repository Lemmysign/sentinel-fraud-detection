package com.sentinel.scoringservice.service;

import com.sentinel.sentinelcommons.event.FraudScoredEvent;

/**
 * Contract for the AI scoring service.
 *
 * Takes the rules engine output, enriches it with
 * AI assessment from Groq, and publishes the final
 * fraud alert event.
 */
public interface ScoringService {

    /**
     * Scores a transaction using Groq AI, combines
     * the result with the rules engine score, and
     * publishes a FraudAlertEvent to Kafka.
     *
     * @param event the scored event from rules engine
     */
    void scoreAndPublish(FraudScoredEvent event);
}