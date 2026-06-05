package com.sentinel.rulesengineservice.engine;

import com.sentinel.sentinelcommons.enums.RiskLevel;
import com.sentinel.sentinelcommons.enums.TransactionStatus;
import com.sentinel.sentinelcommons.event.FraudScoredEvent;
import com.sentinel.sentinelcommons.event.TransactionEvent;
import com.sentinel.rulesengineservice.model.RuleResult;
import com.sentinel.rulesengineservice.rules.AmountThresholdRule;
import com.sentinel.rulesengineservice.rules.BehavioralBaselineRule;
import com.sentinel.rulesengineservice.rules.GeographicAnomalyRule;
import com.sentinel.rulesengineservice.rules.MerchantCategoryRule;
import com.sentinel.rulesengineservice.rules.NewDeviceRule;
import com.sentinel.rulesengineservice.rules.OffHoursRule;
import com.sentinel.rulesengineservice.rules.RecipientRiskRule;
import com.sentinel.rulesengineservice.rules.VelocityRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Slf4j
@Component
@RequiredArgsConstructor
public class RulesEngine {

    // Stateless rules — no infrastructure dependencies
    private final MerchantCategoryRule merchantCategoryRule;
    private final AmountThresholdRule amountThresholdRule;
    private final OffHoursRule offHoursRule;

    // Stateful rules — depend on Redis
    private final VelocityRule velocityRule;
    private final NewDeviceRule newDeviceRule;
    private final GeographicAnomalyRule geographicAnomalyRule;
    private final BehavioralBaselineRule behavioralBaselineRule;
    private final RecipientRiskRule recipientRiskRule;

    /**
     * Evaluates all 8 fraud rules against the transaction
     * and returns a fully scored event ready for Kafka.
     *
     * @param transaction the transaction from the
     *                    transactions.raw Kafka topic
     * @return FraudScoredEvent with score, risk level,
     *         triggered rules, and AI explanation context
     */
    public FraudScoredEvent evaluate(TransactionEvent transaction) {

        log.info("Starting rules evaluation — transaction: {}, " +
                        "account: {}, amount: {} {}",
                transaction.getTransactionId(),
                transaction.getAccountId(),
                transaction.getAmount(),
                transaction.getCurrency());

        // Run all rules — order does not affect correctness
        // Stateless rules first (faster, no I/O)
        // Stateful rules after (Redis calls)
        List<RuleResult> results = List.of(
                merchantCategoryRule.evaluate(transaction),
                amountThresholdRule.evaluate(transaction),
                offHoursRule.evaluate(transaction),
                velocityRule.evaluate(transaction),
                newDeviceRule.evaluate(transaction),
                geographicAnomalyRule.evaluate(transaction),
                behavioralBaselineRule.evaluate(transaction),
                recipientRiskRule.evaluate(transaction)
        );

        // Collect only fired rules
        List<RuleResult> firedRules = results.stream()
                .filter(RuleResult::isFired)
                .collect(Collectors.toList());

        // Sum scores from fired rules, cap at 100
        int totalScore = Math.min(
                firedRules.stream()
                        .mapToInt(RuleResult::getScoreContribution)
                        .sum(),
                100
        );

        // Collect names of fired rules for the event
        List<String> triggeredRuleNames = firedRules.stream()
                .map(RuleResult::getRuleName)
                .collect(Collectors.toList());

        // Build explanation from all fired rule explanations
        // This becomes the context sent to Ollama AI
        // for deeper analysis in the scoring service
        String explanation = firedRules.stream()
                .map(RuleResult::getExplanation)
                .collect(Collectors.joining(" | "));

        RiskLevel riskLevel = determineRiskLevel(totalScore);
        TransactionStatus status = determineStatus(riskLevel);

        log.info("Rules evaluation complete — " +
                        "transaction: {}, score: {}, risk: {}, " +
                        "status: {}, rules fired: {}",
                transaction.getTransactionId(),
                totalScore, riskLevel, status,
                triggeredRuleNames);

        return FraudScoredEvent.builder()
                .transactionId(transaction.getTransactionId())
                .accountId(transaction.getAccountId())
                .merchantId(transaction.getMerchantId())
                .fraudScore(totalScore)
                .riskLevel(riskLevel)
                .status(status)
                .aiExplanation(explanation.isBlank()
                        ? "No fraud signals detected by rules engine."
                        : explanation)
                .triggeredRules(triggeredRuleNames)
                .scoredAt(LocalDateTime.now())
                .build();
    }

    /**
     * Maps a numeric score to a RiskLevel enum.
     *
     * Thresholds are conservative by design —
     * it is cheaper to review a legitimate transaction
     * than to miss real fraud.
     */
    private RiskLevel determineRiskLevel(int score) {
        if (score >= 86) return RiskLevel.CRITICAL;
        if (score >= 61) return RiskLevel.HIGH;
        if (score >= 31) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    /**
     * Maps a RiskLevel to a TransactionStatus.
     *
     * LOW/MEDIUM — APPROVED by rules engine.
     * The scoring service still applies AI analysis
     * before final disposition.
     *
     * HIGH — FLAGGED. Strong signals but human review
     * required. Not blocked yet.
     *
     * CRITICAL — BLOCKED immediately.
     * Multiple strong signals. The probability of
     * a false positive at this score is very low.
     */
    private TransactionStatus determineStatus(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case CRITICAL -> TransactionStatus.BLOCKED;
            case HIGH     -> TransactionStatus.FLAGGED;
            default       -> TransactionStatus.APPROVED;
        };
    }
}