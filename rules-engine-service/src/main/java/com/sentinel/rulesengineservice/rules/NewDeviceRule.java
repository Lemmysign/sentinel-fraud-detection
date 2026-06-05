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
 * New device rule — detects transactions originating
 * from a device not previously seen for this account.
 *
 * Why this is a fraud signal:
 * When credentials are stolen, the fraudster accesses
 * the account from their own device — not the owner's.
 * A transaction from an account's first-ever device
 * is especially suspicious when combined with other
 * anomalies (high amount, off-hours, new location).
 *
 * Redis SET semantics:
 * SADD returns 1 if the element was new (added).
 * SADD returns 0 if the element already existed.
 * This is atomic — safe under concurrency.
 *
 * No TTL on device sets:
 * Known devices accumulate permanently.
 * In production these would sync with the core
 * banking system's enrolled device registry.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewDeviceRule {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${sentinel.rules.device.score-contribution:20}")
    private int scoreContribution;

    public RuleResult evaluate(TransactionEvent transaction) {

        if (transaction.getDeviceId() == null
                || transaction.getDeviceId().isBlank()) {
            return RuleResult.notFired("NEW_DEVICE");
        }

        String key = RedisKeys.DEVICE_PREFIX
                + transaction.getAccountId();

        Long added = redisTemplate.opsForSet()
                .add(key, transaction.getDeviceId());

        boolean isNewDevice = added != null && added > 0;

        if (isNewDevice) {
            String explanation = String.format(
                    "Transaction from device %s — " +
                            "first time seen for account %s.",
                    transaction.getDeviceId(),
                    transaction.getAccountId());

            log.debug("New device — account: {}, device: {}",
                    transaction.getAccountId(),
                    transaction.getDeviceId());

            return RuleResult.fired("NEW_DEVICE_DETECTED",
                    scoreContribution, explanation);
        }

        return RuleResult.notFired("NEW_DEVICE_DETECTED");
    }
}