package com.sentinel.rulesengineservice.rules;

import com.sentinel.sentinelcommons.RedisKeys;
import com.sentinel.sentinelcommons.event.TransactionEvent;
import com.sentinel.rulesengineservice.model.RuleResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Velocity rule — detects unusually high transaction
 * frequency from a single account in a short window.
 *
 * Pattern detected:
 * "This account made N transactions in X seconds"
 *
 * Why this is a fraud signal:
 * Fraudsters draining a compromised account make rapid
 * consecutive transactions to move funds before the
 * account owner notices and freezes the account.
 * Legitimate users rarely exceed 5 transactions in
 * 60 seconds.
 *
 * Redis atomic operations guarantee thread safety:
 * INCR is atomic — no race condition possible.
 * 1000 concurrent requests for the same account
 * each get a unique, correct count.
 *
 * Sliding window implementation:
 * EXPIRE is set only on the first transaction (count == 1).
 * This creates a fixed 60-second window from the first
 * transaction. After 60 seconds the key expires and
 * the counter resets automatically — no cleanup needed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VelocityRule {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${sentinel.rules.velocity.max-transactions:5}")
    private int maxTransactions;

    @Value("${sentinel.rules.velocity.window-seconds:60}")
    private int windowSeconds;

    @Value("${sentinel.rules.velocity.score-contribution:35}")
    private int scoreContribution;

    public RuleResult evaluate(TransactionEvent transaction) {
        String key = RedisKeys.VELOCITY_PREFIX
                + transaction.getAccountId();

        Long count = redisTemplate.opsForValue().increment(key);

        if (count == null) {
            log.warn("Redis INCR returned null for key: {}", key);
            return RuleResult.notFired("VELOCITY_CHECK");
        }

        // Set expiry only on first transaction in window
        if (count == 1) {
            redisTemplate.expire(key,
                    Duration.ofSeconds(windowSeconds));
        }

        log.debug("Velocity — account: {}, count: {}/{} in {}s",
                transaction.getAccountId(),
                count, maxTransactions, windowSeconds);

        if (count > maxTransactions) {
            String explanation = String.format(
                    "Account %s made %d transactions within %d seconds. " +
                            "Threshold: %d transactions per %d seconds.",
                    transaction.getAccountId(), count,
                    windowSeconds, maxTransactions, windowSeconds);

            return RuleResult.fired("VELOCITY_CHECK",
                    scoreContribution, explanation);
        }

        return RuleResult.notFired("VELOCITY_CHECK");
    }
}