package com.sentinel.feedbackservice.service;

import com.sentinel.sentinelcommons.event.FeedbackEvent;

/**
 * Contract for feedback processing operations.
 *
 * Processes analyst decisions to close the AI learning loop.
 * Current implementation logs decisions and measures
 * model accuracy.
 *
 * Designed for extension — future implementations
 * can trigger model retraining, push to ML pipelines,
 * or update feature stores without changing the consumer.
 */
public interface FeedbackService {

    /**
     * Processes an analyst feedback decision.
     *
     * @param event the feedback event from case management
     */
    void processFeedback(FeedbackEvent event);
}