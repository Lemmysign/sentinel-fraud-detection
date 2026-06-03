package com.sentinel.ingestionservice.validator;

import com.sentinel.ingestionservice.dto.TransactionRequest;
import com.sentinel.ingestionservice.exception.TransactionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Business rule validator for incoming transactions.

 * Separation of concerns:
 * - @Valid on the controller handles field-level validation
 *   (is it blank? is it the right format?)
 * - This validator handles business-level validation
 *   (does it make business sense?)

 * Examples of business validation vs field validation:

 * Field:    "amount cannot be null"         → @NotNull
 * Business: "amount cannot exceed 10M NGN"  → this class

 * Field:    "currency must be 3 letters"    → @Pattern
 * Business: "only NGN and USD accepted"     → this class
 *           (depends on business rules, not data format)
 */
@Slf4j
@Component
public class TransactionValidator {

    /**
     * Maximum single transaction amount.
     * 10,000,000 NGN / $10,000 USD equivalent.
     * Transactions above this are automatically suspicious
     * and require manual review outside this system.
     */
    private static final BigDecimal MAX_TRANSACTION_AMOUNT
            = new BigDecimal("10000000.00");

    /**
     * Minimum transaction amount for fraud monitoring.
     * Transactions below this are typically test pings
     * or micro-transactions not worth scoring.
     */
    private static final BigDecimal MIN_MONITORED_AMOUNT
            = new BigDecimal("0.01");

    /**
     * Validates a transaction request against business rules.

     * Throws TransactionException if any rule is violated.
     * The exception is caught by GlobalExceptionHandler
     * and returned as a 400 response.
     *
     * @param request the inbound transaction to validate
     */
    public void validate(TransactionRequest request) {
        validateAmount(request);
        validateCurrency(request);
        validateTransactionType(request);
        log.debug("Transaction validation passed for account: {}",
                request.getAccountId());
    }

    /**
     * Validates the transaction amount against
     * business thresholds.

     * Why not just use @DecimalMax on the DTO?
     * The maximum amount is a business rule that changes
     * based on account type, region, and risk policy.
     * Hardcoding it in an annotation means every policy
     * change requires a code change and redeployment.
     * In a real system this would come from a config
     * service or database. Here we centralise it in
     * the validator for clarity.
     */
    private void validateAmount(TransactionRequest request) {
        if (request.getAmount()
                .compareTo(MAX_TRANSACTION_AMOUNT) > 0) {
            throw new TransactionException(
                    String.format(
                            "Transaction amount %.2f exceeds maximum allowed amount of %.2f",
                            request.getAmount(),
                            MAX_TRANSACTION_AMOUNT
                    ),
                    "AMOUNT_EXCEEDS_LIMIT"
            );
        }
    }

    /**
     * Validates that the currency is one Sentinel
     * currently monitors.

     * Currently, monitors major African and global
     * currencies. Expand this list as needed.
     */
    private void validateCurrency(TransactionRequest request) {
        String currency = request.getCurrency().toUpperCase();  // normalize here

        java.util.Set<String> supportedCurrencies = java.util.Set.of(
                "NGN", "USD", "GBP", "EUR", "KES",
                "GHS", "ZAR", "UGX", "TZS", "XOF"
        );

        if (!supportedCurrencies.contains(currency)) {
            throw new TransactionException(
                    String.format(
                            "Currency %s is not supported. Supported: %s",
                            currency,
                            supportedCurrencies
                    ),
                    "UNSUPPORTED_CURRENCY"
            );
        }
    }

    private void validateTransactionType(TransactionRequest request) {
        String transactionType = request.getTransactionType().toUpperCase();  // normalize here

        java.util.Set<String> validTypes = java.util.Set.of(
                "TRANSFER", "PAYMENT", "WITHDRAWAL", "DEPOSIT"
        );

        if (!validTypes.contains(transactionType)) {
            throw new TransactionException(
                    String.format(
                            "Transaction type %s is not valid",
                            transactionType
                    ),
                    "INVALID_TRANSACTION_TYPE"
            );
        }
    }
}