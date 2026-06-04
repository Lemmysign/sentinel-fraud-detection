package com.sentinel.rulesengineservice.rules;

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
 * Why this matters in fraud:
 * Fraudsters who gain access to an account often make
 * multiple rapid transactions to drain it before the
 * owner notices. Legitimate users rarely make 5+
 * transactions in 60 seconds.
 *
 * How Redis makes this thread-safe:
 * INCR is an atomic operation in Redis. Even if 100
 * concurrent requests arrive for the same account,
 * Redis processes them sequentially — no race conditions,
 * no double-counting, no locks needed on our side.
 *
 * The EXPIRE command sets a TTL on the counter key.
 * After 60 seconds the key disappears automatically —
 * the counter resets without any cleanup code.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VelocityRule {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${sentinel.rules.velocity.max-transactions}")
    private int maxTransactions;

    @Value("${sentinel.rules.velocity.window-seconds}")
    private int windowSeconds;

    @Value("${sentinel.rules.velocity.score-contribution}")
    private int scoreContribution;

    /**
     * Redis key prefix for velocity counters.
     * Full key format: velocity:{accountId}
     * Example: velocity:ACC-001-LAGOS
     */
    private static final String VELOCITY_KEY_PREFIX = "velocity:";

    /**
     * Evaluates velocity for the given transaction.
     *
     * Algorithm:
     * 1. Build Redis key for this account
     * 2. Increment the counter atomically (INCR)
     * 3. If this is the first transaction (count == 1),
     *    set the expiry window (EXPIRE)
     * 4. If count exceeds threshold, rule fires
     *
     * Why set EXPIRE only when count == 1?
     * If we set EXPIRE on every increment, we keep
     * resetting the window — the counter would never
     * expire as long as transactions keep coming.
     * Setting it only on the first increment means
     * the window is fixed from the first transaction.
     */
    public RuleResult evaluate(TransactionEvent transaction) {
        String key = VELOCITY_KEY_PREFIX + transaction.getAccountId();

        // Atomic increment — returns new count
        Long count = redisTemplate.opsForValue().increment(key);

        if (count == null) {
            log.warn("Redis returned null for INCR on key: {}", key);
            return RuleResult.notFired("VELOCITY_CHECK");
        }

        // Set expiry only on first transaction in window
        if (count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }

        log.debug("Velocity check — account: {}, count: {}/{} in {}s window",
                transaction.getAccountId(), count,
                maxTransactions, windowSeconds);

        if (count > maxTransactions) {
            String explanation = String.format(
                    "Account %s made %d transactions in %d seconds. " +
                            "Threshold: %d transactions per %d seconds.",
                    transaction.getAccountId(), count,
                    windowSeconds, maxTransactions, windowSeconds
            );
            return RuleResult.fired("VELOCITY_CHECK",
                    scoreContribution, explanation);
        }

        return RuleResult.notFired("VELOCITY_CHECK");
    }
}