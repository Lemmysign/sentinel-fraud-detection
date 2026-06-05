package com.sentinel.rulesengineservice.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The result of evaluating a single fraud rule
 * against a transaction.
 *
 * Every rule returns one of these — whether it
 * fired (detected something suspicious) and what
 * score it contributes to the overall fraud score.
 *
 * The overall fraud score is the sum of all
 * RuleResult scores. A transaction that triggers
 * multiple rules accumulates a higher score.
 *
 * Example:
 * VelocityRule fires      → score += 35
 * OffHoursRule fires      → score += 15
 * AmountThresholdRule     → does not fire
 * NewDeviceRule fires     → score += 20
 * Total score             = 70 → HIGH risk
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleResult {

    /**
     * Name of the rule that produced this result.
     * Used in FraudScoredEvent.triggeredRules list
     * so analysts know exactly what fired.
     * Examples: "VELOCITY_CHECK", "OFF_HOURS_TRANSACTION"
     */
    private String ruleName;

    /**
     * Whether this rule detected a fraud signal.
     * false = transaction looks normal for this rule
     * true  = suspicious pattern detected
     */
    private boolean fired;

    /**
     * Score contribution if this rule fired.
     * 0 if fired = false.
     * Injected from application.properties so
     * weights can be tuned without code changes.
     */
    private int scoreContribution;

    /**
     * Human-readable explanation of why this rule
     * fired. Included in the AI prompt sent to
     * Ollama for context.
     *
     * Example:
     * "Account ACC-001 made 7 transactions in 45 seconds.
     *  Threshold is 5 transactions in 60 seconds."
     */
    private String explanation;

    /**
     * Convenience factory — creates a result for
     * a rule that did NOT fire.
     */
    public static RuleResult notFired(String ruleName) {
        return RuleResult.builder()
                .ruleName(ruleName)
                .fired(false)
                .scoreContribution(0)
                .explanation(null)
                .build();
    }

    /**
     * Convenience factory — creates a result for
     * a rule that DID fire.
     */
    public static RuleResult fired(String ruleName,
                                   int score,
                                   String explanation) {
        return RuleResult.builder()
                .ruleName(ruleName)
                .fired(true)
                .scoreContribution(score)
                .explanation(explanation)
                .build();
    }
}