package com.sentinel.scoringservice.service.impl;

import com.sentinel.sentinelcommons.enums.RiskLevel;
import com.sentinel.sentinelcommons.enums.TransactionStatus;
import com.sentinel.sentinelcommons.event.FraudAlertEvent;
import com.sentinel.sentinelcommons.event.FraudScoredEvent;
import com.sentinel.scoringservice.model.AiScoringResult;
import com.sentinel.scoringservice.parser.AiResponseParser;
import com.sentinel.scoringservice.prompt.FraudScoringPrompt;
import com.sentinel.scoringservice.service.ScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * AI-powered fraud scoring service.
 *
 * Combines two scoring signals:
 *
 * 1. Rules Engine Score (60% weight)
 *    Deterministic, explainable, fast.
 *    Based on concrete fraud patterns.
 *
 * 2. AI Score from Groq (40% weight)
 *    Contextual, pattern-aware, adaptive.
 *    Can detect novel fraud patterns rules miss.
 *
 * Final score = (rulesScore × 0.6) + (aiScore × 0.4)
 *
 * Why 60/40 weighting?
 * Rules are auditable and consistent — they form the
 * backbone of the decision. AI is more powerful but
 * less predictable — it enhances the rules but does
 * not override them. This balance ensures the system
 * remains explainable to regulators while benefiting
 * from AI intelligence.
 *
 * Resilience design:
 * If Groq API is unavailable or returns an error,
 * the service falls back to the rules engine score
 * alone (100% weight). The pipeline never stops
 * because AI is unavailable.
 *
 * Only HIGH and CRITICAL risk levels publish
 * FraudAlertEvents — LOW and MEDIUM are approved
 * without creating a case management record.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoringServiceImpl implements ScoringService {

    private final ChatClient chatClient;
    private final FraudScoringPrompt fraudScoringPrompt;
    private final AiResponseParser aiResponseParser;
    private final KafkaTemplate<String, FraudAlertEvent> kafkaTemplate;

    @Value("${sentinel.kafka.topics.fraud-alerts}")
    private String fraudAlertsTopic;

    /**
     * Rules engine score weight — 60%
     */
    private static final double RULES_WEIGHT = 0.6;

    /**
     * AI score weight — 40%
     */
    private static final double AI_WEIGHT = 0.4;

    @Override
    public void scoreAndPublish(FraudScoredEvent event) {
        log.info("Starting AI scoring — transaction: {}, " +
                        "rules score: {}, risk: {}",
                event.getTransactionId(),
                event.getFraudScore(),
                event.getRiskLevel());

        // Step 1 — Get AI assessment from Groq
        AiScoringResult aiResult = getAiAssessment(event);

        // Step 2 — Calculate combined final score
        int finalScore = calculateFinalScore(
                event.getFraudScore(),
                aiResult.getAiScore(),
                aiResult.isParsedSuccessfully());

        // Step 3 — Determine final risk level and status
        RiskLevel finalRiskLevel = determineRiskLevel(finalScore);
        TransactionStatus finalStatus =
                determineStatus(finalRiskLevel);

        log.info("Scoring complete — transaction: {}, " +
                        "rules: {}, ai: {}, final: {}, " +
                        "risk: {}, status: {}",
                event.getTransactionId(),
                event.getFraudScore(),
                aiResult.getAiScore(),
                finalScore,
                finalRiskLevel,
                finalStatus);

        // Step 4 — Only publish alerts for HIGH and CRITICAL
        // LOW and MEDIUM are approved silently
        if (finalRiskLevel == RiskLevel.HIGH
                || finalRiskLevel == RiskLevel.CRITICAL) {
            publishFraudAlert(event, aiResult,
                    finalScore, finalRiskLevel);
        } else {
            log.info("Transaction approved — id: {}, " +
                            "final score: {}, risk: {}",
                    event.getTransactionId(),
                    finalScore, finalRiskLevel);
        }
    }

    /**
     * Calls Groq AI via Spring AI ChatClient.
     *
     * The prompt gives the AI full context:
     * transaction details, rules that fired,
     * scores, and explanations.
     *
     * On any failure (API error, timeout, rate limit)
     * returns a fallback result with parsedSuccessfully=false
     * so the caller can handle gracefully.
     */
    private AiScoringResult getAiAssessment(
            FraudScoredEvent event) {
        try {
            String prompt = fraudScoringPrompt
                    .buildPrompt(event);

            log.debug("Sending fraud assessment prompt " +
                            "to Groq — transaction: {}",
                    event.getTransactionId());

            /*
             * ChatClient.prompt() — Spring AI fluent API
             * .user()   — the message sent to the model
             * .call()   — executes the API call
             * .content() — extracts the text response
             *
             * This is synchronous — we wait for the response
             * before continuing. Groq's median latency is
             * ~200-400ms — acceptable for fraud scoring.
             *
             * For extreme performance requirements this
             * could be made reactive but synchronous is
             * simpler and sufficient here.
             */
            String aiResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.debug("Groq response received — " +
                            "transaction: {}, response: {}",
                    event.getTransactionId(), aiResponse);

            return aiResponseParser.parse(
                    aiResponse,
                    event.getTransactionId());

        } catch (Exception e) {
            log.error("Groq AI call failed — " +
                            "transaction: {}, error: {}. " +
                            "Falling back to rules score only.",
                    event.getTransactionId(),
                    e.getMessage());

            // Graceful degradation — use neutral AI score
            return AiScoringResult.builder()
                    .aiScore(event.getFraudScore())
                    .riskAssessment("AI unavailable")
                    .recommendation("FLAG")
                    .reasoning("Groq API unavailable. " +
                            "Decision based on rules engine only.")
                    .parsedSuccessfully(false)
                    .build();
        }
    }

    /**
     * Combines rules engine score and AI score.
     *
     * If AI parsing failed — use rules score at full weight.
     * If AI parsing succeeded — weighted combination.
     *
     * Score is capped at 100.
     */
    private int calculateFinalScore(int rulesScore,
                                    int aiScore,
                                    boolean aiSucceeded) {
        if (!aiSucceeded) {
            log.debug("AI parsing failed — using rules " +
                    "score only: {}", rulesScore);
            return rulesScore;
        }

        int combined = (int) Math.round(
                (rulesScore * RULES_WEIGHT)
                        + (aiScore * AI_WEIGHT));

        return Math.min(combined, 100);
    }

    /**
     * Publishes a FraudAlertEvent for HIGH/CRITICAL
     * transactions. This triggers case management
     * to create a fraud case for analyst review.
     */
    private void publishFraudAlert(FraudScoredEvent event,
                                   AiScoringResult aiResult,
                                   int finalScore,
                                   RiskLevel finalRiskLevel) {

        // Build combined explanation from rules + AI
        String combinedExplanation = buildCombinedExplanation(
                event, aiResult);

        FraudAlertEvent alertEvent = FraudAlertEvent.builder()
                .transactionId(event.getTransactionId())
                .caseId(generateCaseId(event.getTransactionId()))
                .accountId(event.getAccountId())
                .riskLevel(finalRiskLevel)
                .fraudScore(finalScore)
                .triggeredRules(event.getTriggeredRules())
                .aiExplanation(combinedExplanation)
                .alertCreatedAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(
                fraudAlertsTopic,
                alertEvent.getTransactionId(),
                alertEvent
        ).whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("FraudAlertEvent published — " +
                                "transaction: {}, case: {}, " +
                                "score: {}, risk: {}",
                        alertEvent.getTransactionId(),
                        alertEvent.getCaseId(),
                        alertEvent.getFraudScore(),
                        alertEvent.getRiskLevel());
            } else {
                log.error("Failed to publish FraudAlertEvent " +
                                "— transaction: {}, error: {}",
                        alertEvent.getTransactionId(),
                        ex.getMessage(), ex);
            }
        });
    }

    /**
     * Combines rules engine explanation with AI reasoning
     * into a single explanation stored on the fraud case.
     * Analysts see both the rule-based evidence and
     * the AI's contextual assessment.
     */
    private String buildCombinedExplanation(
            FraudScoredEvent event,
            AiScoringResult aiResult) {

        StringBuilder sb = new StringBuilder();

        sb.append("=== RULES ENGINE ===\n");
        if (event.getAiExplanation() != null
                && !event.getAiExplanation().isBlank()) {
            sb.append(event.getAiExplanation());
        } else {
            sb.append("No rules fired.");
        }

        sb.append("\n\n=== AI ASSESSMENT (Groq) ===\n");
        sb.append("Risk: ").append(aiResult.getRiskAssessment())
                .append("\n");
        sb.append("Recommendation: ")
                .append(aiResult.getRecommendation())
                .append("\n");
        sb.append("Reasoning: ").append(aiResult.getReasoning());

        return sb.toString();
    }

    /**
     * Generates a case ID for the fraud alert.
     * Format: CASE-{first 8 chars of transaction ID}
     * Example: CASE-5bdccaa2
     *
     * In production this would be a sequential ID
     * from the case management database.
     */
    private String generateCaseId(String transactionId) {
        return "CASE-" + transactionId.substring(0, 8)
                .toUpperCase();
    }

    private RiskLevel determineRiskLevel(int score) {
        if (score >= 86) return RiskLevel.CRITICAL;
        if (score >= 61) return RiskLevel.HIGH;
        if (score >= 31) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    private TransactionStatus determineStatus(
            RiskLevel riskLevel) {
        return switch (riskLevel) {
            case CRITICAL -> TransactionStatus.BLOCKED;
            case HIGH     -> TransactionStatus.FLAGGED;
            default       -> TransactionStatus.APPROVED;
        };
    }
}