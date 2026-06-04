package com.sentinel.rulesengineservice.rules;

import com.sentinel.sentinelcommons.event.TransactionEvent;
import com.sentinel.rulesengineservice.model.RuleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

/**
 * Off-hours rule — detects transactions made during
 * statistically high-risk time windows.
 *
 * Why off-hours matters:
 * Fraudsters operating in different time zones or
 * using automated scripts often act during periods
 * when account owners are asleep and less likely
 * to notice and report fraud immediately.
 *
 * High-risk window: 01:00 — 05:00 (1am to 5am)
 *
 * This is a contributing signal, not a block reason.
 * A legitimate off-hours transaction (night shift worker,
 * traveller in different timezone) combined with a low
 * AI score will still be approved.
 */
@Slf4j
@Component
public class OffHoursRule {

    @Value("${sentinel.rules.off-hours.score-contribution}")
    private int scoreContribution;

    /**
     * High-risk time window start — 1am
     */
    private static final LocalTime RISK_WINDOW_START =
            LocalTime.of(1, 0);

    /**
     * High-risk time window end — 5am
     */
    private static final LocalTime RISK_WINDOW_END =
            LocalTime.of(5, 0);

    public RuleResult evaluate(TransactionEvent transaction) {
        if (transaction.getTimestamp() == null) {
            return RuleResult.notFired("OFF_HOURS");
        }

        LocalTime transactionTime =
                transaction.getTimestamp().toLocalTime();

        boolean isOffHours = transactionTime.isAfter(RISK_WINDOW_START)
                && transactionTime.isBefore(RISK_WINDOW_END);

        if (isOffHours) {
            String explanation = String.format(
                    "Transaction initiated at %s, which falls within " +
                            "the high-risk window (%s - %s).",
                    transactionTime,
                    RISK_WINDOW_START,
                    RISK_WINDOW_END
            );

            log.debug("Off-hours rule fired — account: {}, time: {}",
                    transaction.getAccountId(), transactionTime);

            return RuleResult.fired("OFF_HOURS_TRANSACTION",
                    scoreContribution, explanation);
        }

        return RuleResult.notFired("OFF_HOURS_TRANSACTION");
    }
}