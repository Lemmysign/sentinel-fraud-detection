package com.sentinel.rulesengineservice.rules;

import com.sentinel.sentinelcommons.RedisKeys;
import com.sentinel.sentinelcommons.event.TransactionEvent;
import com.sentinel.rulesengineservice.model.RuleResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Behavioral baseline rule — detects transactions that
 * deviate significantly from an account's normal pattern.
 *
 * Baseline model:
 * Tracks a running sum and count of transaction amounts
 * per account. Average = sum / count.
 * Fires when: current amount > average × deviationMultiplier
 *
 * Minimum transaction requirement:
 * The first minTransactions are used to BUILD the baseline
 * before this rule starts firing. This prevents false
 * positives for new accounts with no history.
 *
 * KNOWN LIMITATION — Race condition under high concurrency:
 * The sum and count are updated in two separate Redis
 * operations. Under extreme concurrency (two transactions
 * for the same account arriving simultaneously), one
 * update could overwrite the other — causing the baseline
 * to drift slightly.
 *
 * Production fix:
 * Use a Redis Lua script to make the read-modify-write
 * atomic, or use Redis HINCRBYFLOAT for the sum and
 * INCR for the count — both are atomic operations.
 *
 * For this project the drift is acceptable — baseline
 * calculations are approximate by nature and the
 * velocity of same-account concurrent transactions
 * is extremely low in practice.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BehavioralBaselineRule {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${sentinel.rules.baseline.deviation-multiplier:5}")
    private double deviationMultiplier;

    @Value("${sentinel.rules.baseline.min-transactions:3}")
    private int minTransactions;

    @Value("${sentinel.rules.baseline.score-contribution:30}")
    private int scoreContribution;

    public RuleResult evaluate(TransactionEvent transaction) {
        String sumKey = RedisKeys.BASELINE_SUM_PREFIX
                + transaction.getAccountId();
        String countKey = RedisKeys.BASELINE_COUNT_PREFIX
                + transaction.getAccountId();

        String sumStr = redisTemplate.opsForValue().get(sumKey);
        String countStr = redisTemplate.opsForValue().get(countKey);

        long count = countStr != null
                ? Long.parseLong(countStr) : 0;
        BigDecimal sum = sumStr != null
                ? new BigDecimal(sumStr) : BigDecimal.ZERO;

        // Update baseline with current transaction amount
        BigDecimal newSum = sum.add(transaction.getAmount());
        long newCount = count + 1;

        redisTemplate.opsForValue().set(
                sumKey, newSum.toPlainString());
        redisTemplate.opsForValue().set(
                countKey, String.valueOf(newCount));

        // Not enough history — baseline not yet meaningful
        if (count < minTransactions) {
            log.debug("Baseline building — account: {}, " +
                            "transactions seen: {}/{}",
                    transaction.getAccountId(),
                    count, minTransactions);
            return RuleResult.notFired("BEHAVIORAL_BASELINE");
        }

        // Calculate average from PREVIOUS transactions
        // Do not include current transaction in average —
        // we are comparing current against past behaviour
        BigDecimal average = sum.divide(
                BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);

        BigDecimal threshold = average.multiply(
                BigDecimal.valueOf(deviationMultiplier));

        log.debug("Baseline check — account: {}, avg: {}, " +
                        "threshold: {}, current: {}",
                transaction.getAccountId(),
                average, threshold, transaction.getAmount());

        if (transaction.getAmount().compareTo(threshold) > 0) {
            double multiplierActual = transaction.getAmount()
                    .divide(average, 2, RoundingMode.HALF_UP)
                    .doubleValue();

            String explanation = String.format(
                    "Transaction amount %.2f %s is %.1fx the " +
                            "account average of %.2f %s " +
                            "(based on %d transactions). " +
                            "Deviation threshold: %.0fx average.",
                    transaction.getAmount(),
                    transaction.getCurrency(),
                    multiplierActual,
                    average,
                    transaction.getCurrency(),
                    count,
                    deviationMultiplier);

            return RuleResult.fired("BEHAVIORAL_BASELINE",
                    scoreContribution, explanation);
        }

        return RuleResult.notFired("BEHAVIORAL_BASELINE");
    }
}