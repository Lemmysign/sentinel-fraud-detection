package com.sentinel.rulesengineservice.rules;

import com.sentinel.sentinelcommons.event.TransactionEvent;
import com.sentinel.rulesengineservice.model.RuleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Amount threshold rule — detects transactions whose
 * amount is suspiciously large for their type.
 *
 * Different transaction types have different normal
 * amount ranges. A ₦5,000,000 TRANSFER is more
 * suspicious than a ₦5,000,000 DEPOSIT.
 *
 * Thresholds by transaction type (in base currency units):
 * TRANSFER    → flag above ₦2,000,000 ($2,000)
 * PAYMENT     → flag above ₦500,000  ($500)
 * WITHDRAWAL  → flag above ₦1,000,000 ($1,000)
 * DEPOSIT     → flag above ₦5,000,000 ($5,000)
 *
 * In production these thresholds would come from
 * a rules configuration service per account tier.
 */
@Slf4j
@Component
public class AmountThresholdRule {

    @Value("${sentinel.rules.amount.score-contribution}")
    private int scoreContribution;

    private static final Map<String, BigDecimal> THRESHOLDS =
            Map.of(
                    "TRANSFER",   new BigDecimal("2000000.00"),
                    "PAYMENT",    new BigDecimal("500000.00"),
                    "WITHDRAWAL", new BigDecimal("1000000.00"),
                    "DEPOSIT",    new BigDecimal("5000000.00")
            );

    /**
     * Default threshold for unknown transaction types.
     */
    private static final BigDecimal DEFAULT_THRESHOLD =
            new BigDecimal("1000000.00");

    public RuleResult evaluate(TransactionEvent transaction) {
        BigDecimal threshold = THRESHOLDS.getOrDefault(
                transaction.getTransactionType(),
                DEFAULT_THRESHOLD
        );

        if (transaction.getAmount().compareTo(threshold) > 0) {
            String explanation = String.format(
                    "Transaction amount %.2f %s exceeds threshold of %.2f " +
                            "for transaction type %s.",
                    transaction.getAmount(),
                    transaction.getCurrency(),
                    threshold,
                    transaction.getTransactionType()
            );

            log.debug("Amount threshold fired — account: {}, amount: {}, threshold: {}",
                    transaction.getAccountId(),
                    transaction.getAmount(),
                    threshold);

            return RuleResult.fired("AMOUNT_THRESHOLD",
                    scoreContribution, explanation);
        }

        return RuleResult.notFired("AMOUNT_THRESHOLD");
    }
}