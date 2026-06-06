package com.sentinel.feedbackservice.service.impl;

import com.sentinel.sentinelcommons.enums.FraudDecision;
import com.sentinel.sentinelcommons.enums.RiskLevel;
import com.sentinel.sentinelcommons.event.FeedbackEvent;
import com.sentinel.feedbackservice.model.FeedbackRecord;
import com.sentinel.feedbackservice.service.FeedbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Processes analyst feedback decisions.
 *
 * Responsibilities:
 * 1. Convert FeedbackEvent to FeedbackRecord
 * 2. Determine if the model assessment was correct
 * 3. Log the outcome for observability
 * 4. Log model accuracy signals
 *
 * Model accuracy measurement:
 * Every feedback event tells us whether the AI+rules
 * assessment was correct:
 *
 * CONFIRMED_FRAUD  → model was right to flag it (true positive)
 * FALSE_POSITIVE   → model was wrong to flag it (false positive)
 * UNDER_REVIEW     → decision pending, skip accuracy measurement
 *
 * Tracking this over time gives the model accuracy rate:
 * accuracy = confirmed / (confirmed + false_positive)
 *
 * A healthy fraud detection system targets >85% accuracy.
 * Below 70% means too many false positives — customers
 * are being incorrectly blocked, hurting user experience.
 */
@Slf4j
@Service
public class FeedbackServiceImpl implements FeedbackService {

    @Override
    public void processFeedback(FeedbackEvent event) {

        // Build internal record from Kafka event
        FeedbackRecord record = buildRecord(event);

        // Log the processed feedback
        logFeedback(record);

        // Log model accuracy signal
        logModelAccuracySignal(record);

        // TODO: Production extension point 1
        // Persist to time-series database for trend analysis
        // feedbackRepository.save(record);

        // TODO: Production extension point 2
        // If accuracy drops below threshold, trigger retraining
        // if (accuracyTracker.getCurrentAccuracy() < 0.70) {
        //     modelRetrainingService.triggerRetraining();
        // }

        // TODO: Production extension point 3
        // Push to ML feature store for online learning
        // featureStoreClient.updateFeatures(record);

        // TODO: Production extension point 4
        // Update Groq AI system prompt based on patterns
        // promptEngineeringService.updatePromptWithPattern(record);
    }

    /**
     * Converts a FeedbackEvent into an internal FeedbackRecord.
     *
     * Determines model correctness:
     * The model flagged this transaction as HIGH or CRITICAL.
     * Was it right?
     * - CONFIRMED_FRAUD → yes, model was correct
     * - FALSE_POSITIVE  → no, model was wrong
     * - UNDER_REVIEW    → unknown, skip
     */
    private FeedbackRecord buildRecord(FeedbackEvent event) {
        boolean modelWasCorrect = determineModelCorrectness(
                event.getAnalystDecision(),
                event.getOriginalRiskLevel());

        return FeedbackRecord.builder()
                .transactionId(event.getTransactionId())
                .caseId(event.getCaseId())
                .accountId(event.getAccountId())
                .originalRiskLevel(event.getOriginalRiskLevel())
                .originalFraudScore(event.getOriginalFraudScore())
                .analystDecision(event.getAnalystDecision())
                .analystNotes(event.getAnalystNotes())
                .decidedAt(event.getDecidedAt())
                .modelWasCorrect(modelWasCorrect)
                .processedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Determines whether the model's assessment was correct.
     *
     * The model already decided this was HIGH or CRITICAL
     * (otherwise it would not have created a case).
     * The analyst's decision tells us if the model was right.
     */
    private boolean determineModelCorrectness(
            FraudDecision decision,
            RiskLevel originalRiskLevel) {

        if (decision == null) return false;

        return switch (decision) {
            // Model flagged it → analyst confirmed → correct
            case CONFIRMED_FRAUD -> true;
            // Model flagged it → analyst cleared it → incorrect
            case FALSE_POSITIVE -> false;
            // Still under review → unknown
            case UNDER_REVIEW -> false;
        };
    }

    /**
     * Logs the feedback outcome.
     * Structured logging enables log aggregation tools
     * (Grafana, ELK) to parse and visualise feedback trends.
     */
    private void logFeedback(FeedbackRecord record) {
        log.info("Feedback processed — " +
                        "transaction: {}, case: {}, " +
                        "account: {}, decision: {}, " +
                        "original score: {}, original risk: {}, " +
                        "model correct: {}",
                record.getTransactionId(),
                record.getCaseId(),
                record.getAccountId(),
                record.getAnalystDecision(),
                record.getOriginalFraudScore(),
                record.getOriginalRiskLevel(),
                record.isModelWasCorrect());

        // Log analyst notes if provided — valuable context
        if (record.getAnalystNotes() != null
                && !record.getAnalystNotes().isBlank()) {
            log.info("Analyst notes for transaction {}: {}",
                    record.getTransactionId(),
                    record.getAnalystNotes());
        }
    }

    /**
     * Logs model accuracy signals.
     *
     * These log lines are designed to be parsed by
     * Grafana or any log aggregation tool to build
     * real-time accuracy dashboards.
     *
     * Log format is deliberately structured so tools
     * can extract: outcome, risk_level, score fields.
     */
    private void logModelAccuracySignal(FeedbackRecord record) {
        if (record.getAnalystDecision() ==
                FraudDecision.UNDER_REVIEW) {
            // Not yet decided — skip accuracy measurement
            return;
        }

        if (record.isModelWasCorrect()) {
            log.info("MODEL_ACCURACY outcome=TRUE_POSITIVE " +
                            "risk_level={} score={} " +
                            "transaction={}",
                    record.getOriginalRiskLevel(),
                    record.getOriginalFraudScore(),
                    record.getTransactionId());
        } else {
            log.warn("MODEL_ACCURACY outcome=FALSE_POSITIVE " +
                            "risk_level={} score={} " +
                            "transaction={} — " +
                            "model flagged legitimate transaction",
                    record.getOriginalRiskLevel(),
                    record.getOriginalFraudScore(),
                    record.getTransactionId());
        }
    }
}