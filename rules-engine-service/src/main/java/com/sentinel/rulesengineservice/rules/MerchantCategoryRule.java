package com.sentinel.rulesengineservice.rules;


import com.sentinel.sentinelcommons.event.TransactionEvent;
import com.sentinel.rulesengineservice.model.RuleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Merchant category rule — detects transactions sent to
 * high-risk merchant categories.
 *
 * Why certain merchants are higher risk:
 * Crypto exchanges allow instant conversion of stolen
 * funds to untraceable assets. Gambling sites enable
 * rapid fund movement and money laundering. These
 * categories appear disproportionately in confirmed
 * fraud cases across the industry.
 *
 * Three-tier risk classification:
 * CRITICAL — crypto, darknet, anonymous transfers
 * HIGH     — gambling, adult content, forex
 * MEDIUM   — prepaid cards, money transfer services
 *
 * Matching strategy:
 * Merchant IDs are checked against keyword maps.
 * In production this would use merchant category codes
 * (MCC) from the card network — a standardised 4-digit
 * code assigned to every registered merchant.
 * Keywords are used here as a practical approximation.
 */
@Slf4j
@Component
public class MerchantCategoryRule {

    @Value("${sentinel.rules.merchant.score-contribution-critical:40}")
    private int criticalScore;

    @Value("${sentinel.rules.merchant.score-contribution-high:25}")
    private int highScore;

    @Value("${sentinel.rules.merchant.score-contribution-medium:15}")
    private int mediumScore;

    /**
     * Merchant ID keywords mapped to risk tiers.
     * Checked case-insensitively against the merchantId.
     *
     * CRITICAL — immediate high-score signal
     * HIGH     — strong signal, combine with other rules
     * MEDIUM   — contributing signal only
     */
    private static final Map<String, Set<String>> RISK_KEYWORDS =
            Map.of(
                    "CRITICAL", Set.of(
                            "crypto", "bitcoin", "binance",
                            "coinbase", "darknet", "anonymous",
                            "mixer", "tumbler"
                    ),
                    "HIGH", Set.of(
                            "casino", "gambling", "bet", "poker",
                            "lottery", "adult", "forex", "fx-trade"
                    ),
                    "MEDIUM", Set.of(
                            "prepaid", "giftcard", "moneygram",
                            "westernunion", "remit", "transfer"
                    )
            );

    public RuleResult evaluate(TransactionEvent transaction) {

        if (transaction.getMerchantId() == null) {
            return RuleResult.notFired("MERCHANT_CATEGORY_RISK");
        }

        String merchantIdLower =
                transaction.getMerchantId().toLowerCase();

        // Check CRITICAL tier first — highest score
        for (String keyword : RISK_KEYWORDS.get("CRITICAL")) {
            if (merchantIdLower.contains(keyword)) {
                String explanation = String.format(
                        "Merchant %s matches CRITICAL risk category " +
                                "keyword '%s'. Associated with crypto/anonymous " +
                                "fund movement.",
                        transaction.getMerchantId(), keyword);

                log.debug("Merchant category CRITICAL — " +
                                "merchant: {}, keyword: {}",
                        transaction.getMerchantId(), keyword);

                return RuleResult.fired(
                        "MERCHANT_CATEGORY_RISK",
                        criticalScore, explanation);
            }
        }

        // Check HIGH tier
        for (String keyword : RISK_KEYWORDS.get("HIGH")) {
            if (merchantIdLower.contains(keyword)) {
                String explanation = String.format(
                        "Merchant %s matches HIGH risk category " +
                                "keyword '%s'. Associated with gambling " +
                                "or high-risk financial services.",
                        transaction.getMerchantId(), keyword);

                log.debug("Merchant category HIGH — " +
                                "merchant: {}, keyword: {}",
                        transaction.getMerchantId(), keyword);

                return RuleResult.fired(
                        "MERCHANT_CATEGORY_RISK",
                        highScore, explanation);
            }
        }

        // Check MEDIUM tier
        for (String keyword : RISK_KEYWORDS.get("MEDIUM")) {
            if (merchantIdLower.contains(keyword)) {
                String explanation = String.format(
                        "Merchant %s matches MEDIUM risk category " +
                                "keyword '%s'. Associated with money " +
                                "transfer services.",
                        transaction.getMerchantId(), keyword);

                log.debug("Merchant category MEDIUM — " +
                                "merchant: {}, keyword: {}",
                        transaction.getMerchantId(), keyword);

                return RuleResult.fired(
                        "MERCHANT_CATEGORY_RISK",
                        mediumScore, explanation);
            }
        }

        return RuleResult.notFired("MERCHANT_CATEGORY_RISK");
    }
}