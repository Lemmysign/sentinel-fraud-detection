package com.sentinel.rulesengineservice.rules;

import com.sentinel.sentinelcommons.RedisKeys;
import com.sentinel.sentinelcommons.event.TransactionEvent;
import com.sentinel.rulesengineservice.model.RuleResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Recipient risk rule — detects transactions sent to
 * merchants or recipients with a history of fraud cases.
 *
 * Why this matters:
 * Money mule accounts receive funds from many fraud
 * victims before being shut down. A merchant that
 * repeatedly appears in confirmed fraud cases is either
 * compromised, complicit, or being used as a mule.
 *
 * How it works:
 * This rule READS a counter from Redis.
 * case-management-service WRITES the counter when an
 * analyst confirms a fraud case.
 *
 * Redis key: recipient:risk:{merchantId}
 * Defined in RedisKeys.RECIPIENT_RISK_PREFIX so both
 * services use identical key format without duplication.
 *
 * The feedback loop:
 * 1. Fraud confirmed by analyst
 * 2. case-management increments recipient:risk:{merchantId}
 * 3. Next transaction to same recipient scores higher
 * 4. System learns from real confirmed fraud
 *
 * Three-tier scoring:
 * 1-2 cases  → MEDIUM (score 20) — worth noting
 * 3-5 cases  → HIGH   (score 35) — strong signal
 * 6+ cases   → CRITICAL (score 50) — known fraud recipient
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecipientRiskRule {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${sentinel.rules.recipient.medium-threshold:1}")
    private int mediumThreshold;

    @Value("${sentinel.rules.recipient.high-threshold:3}")
    private int highThreshold;

    @Value("${sentinel.rules.recipient.critical-threshold:6}")
    private int criticalThreshold;

    @Value("${sentinel.rules.recipient.score-medium:20}")
    private int mediumScore;

    @Value("${sentinel.rules.recipient.score-high:35}")
    private int highScore;

    @Value("${sentinel.rules.recipient.score-critical:50}")
    private int criticalScore;

    public RuleResult evaluate(TransactionEvent transaction) {

        if (transaction.getMerchantId() == null) {
            return RuleResult.notFired("RECIPIENT_RISK");
        }

        String key = RedisKeys.RECIPIENT_RISK_PREFIX
                + transaction.getMerchantId();

        String countStr = redisTemplate.opsForValue().get(key);

        if (countStr == null) {
            return RuleResult.notFired("RECIPIENT_RISK");
        }

        int fraudCaseCount;
        try {
            fraudCaseCount = Integer.parseInt(countStr);
        } catch (NumberFormatException e) {
            log.warn("Invalid recipient risk counter for " +
                    "merchant: {}", transaction.getMerchantId());
            return RuleResult.notFired("RECIPIENT_RISK");
        }

        if (fraudCaseCount < mediumThreshold) {
            return RuleResult.notFired("RECIPIENT_RISK");
        }

        int score;
        String riskTier;

        if (fraudCaseCount >= criticalThreshold) {
            score = criticalScore;
            riskTier = "CRITICAL";
        } else if (fraudCaseCount >= highThreshold) {
            score = highScore;
            riskTier = "HIGH";
        } else {
            score = mediumScore;
            riskTier = "MEDIUM";
        }

        String explanation = String.format(
                "Recipient %s has been associated with %d " +
                        "confirmed fraud case(s). Risk tier: %s.",
                transaction.getMerchantId(),
                fraudCaseCount, riskTier);

        log.debug("Recipient risk fired — merchant: {}, " +
                        "cases: {}, tier: {}",
                transaction.getMerchantId(),
                fraudCaseCount, riskTier);

        return RuleResult.fired("RECIPIENT_RISK",
                score, explanation);
    }
}