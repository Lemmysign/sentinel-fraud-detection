package com.sentinel.rulesengineservice.rules;

import com.sentinel.sentinelcommons.event.TransactionEvent;
import com.sentinel.rulesengineservice.model.RuleResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * New device rule — detects when a transaction comes
 * from a device not previously seen for this account.
 *
 * Why this is a fraud signal:
 * When a fraudster gains account credentials, they
 * access the account from their own device — not the
 * legitimate owner's device. A new device on an
 * established account is a meaningful signal,
 * especially when combined with other rules.
 *
 * How device history is tracked:
 * Redis SET data structure stores known device IDs
 * per account. SADD adds a device and returns 1 if
 * new (first time seen) or 0 if already known.
 *
 * Redis key format: devices:{accountId}
 * Redis data type:  SET of device IDs
 *
 * No expiry is set on device sets — known devices
 * accumulate over time, which is correct behaviour.
 * In production these would sync with the core
 * banking system's device registry.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewDeviceRule {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${sentinel.rules.device.score-contribution}")
    private int scoreContribution;

    private static final String DEVICE_KEY_PREFIX = "devices:";

    public RuleResult evaluate(TransactionEvent transaction) {

        // No device ID provided — skip this check
        if (transaction.getDeviceId() == null
                || transaction.getDeviceId().isBlank()) {
            return RuleResult.notFired("NEW_DEVICE");
        }

        String key = DEVICE_KEY_PREFIX + transaction.getAccountId();

        /*
         * SADD returns the number of elements added.
         * 1 = this device is NEW (never seen before)
         * 0 = this device is KNOWN (seen before)
         *
         * This operation is atomic — safe under concurrency.
         */
        Long addedCount = redisTemplate.opsForSet()
                .add(key, transaction.getDeviceId());

        boolean isNewDevice = addedCount != null && addedCount > 0;

        if (isNewDevice) {
            String explanation = String.format(
                    "Transaction initiated from device %s which has " +
                            "not been seen before for account %s.",
                    transaction.getDeviceId(),
                    transaction.getAccountId()
            );

            log.debug("New device rule fired — account: {}, device: {}",
                    transaction.getAccountId(),
                    transaction.getDeviceId());

            return RuleResult.fired("NEW_DEVICE_DETECTED",
                    scoreContribution, explanation);
        }

        return RuleResult.notFired("NEW_DEVICE_DETECTED");
    }
}