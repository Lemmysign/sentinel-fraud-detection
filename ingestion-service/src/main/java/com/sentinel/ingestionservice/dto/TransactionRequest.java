package com.sentinel.ingestionservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Inbound DTO — what an external system (bank, fintech,
 * lender) sends to Sentinel when submitting a transaction
 * for fraud assessment.
 *
 * Validation annotations enforce data quality at the
 * HTTP boundary before any business logic runs.
 * Bad data is rejected immediately with a clear error
 * message — it never reaches the service layer.
 *
 * Why BigDecimal for amount?
 * Financial calculations require exact decimal precision.
 * double and float use binary floating point which cannot
 * represent many decimal values exactly — 0.1 + 0.2 = 0.30000000000000004.
 * BigDecimal guarantees exact representation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequest {

    /**
     * The account initiating the transaction.
     * Cannot be blank — every transaction must
     * be traceable to an account.
     */
    @NotBlank(message = "Account ID is required")
    @Size(max = 50, message = "Account ID must not exceed 50 characters")
    private String accountId;

    /**
     * The merchant or recipient.
     */
    @NotBlank(message = "Merchant ID is required")
    @Size(max = 50, message = "Merchant ID must not exceed 50 characters")
    private String merchantId;

    /**
     * Transaction amount — must be greater than zero.
     * A zero or negative amount is invalid for any
     * legitimate payment transaction.
     */
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    /**
     * ISO 4217 currency code — exactly 3 uppercase letters.
     * Examples: NGN, USD, GBP, EUR, KES
     * Regex enforces the standard format.
     */
    @NotBlank(message = "Currency is required")
    @Pattern(
            regexp = "^[A-Z]{3}$",
            message = "Currency must be a valid 3-letter ISO 4217 code (e.g. NGN, USD)"
    )
    private String currency;

    /**
     * Type of transaction.
     * Allowed values: TRANSFER, PAYMENT, WITHDRAWAL, DEPOSIT
     * Different types carry different fraud risk profiles —
     * a WITHDRAWAL at 2am is riskier than a PAYMENT at noon.
     */
    @NotBlank(message = "Transaction type is required")
    @Pattern(
            regexp = "^(TRANSFER|PAYMENT|WITHDRAWAL|DEPOSIT)$",
            message = "Transaction type must be TRANSFER, PAYMENT, WITHDRAWAL, or DEPOSIT"
    )
    private String transactionType;

    /**
     * IP address of the originating device.
     * Optional — mobile apps may not always provide this.
     * Used for geographic anomaly detection when present.
     */
    @Size(max = 45, message = "Source IP must not exceed 45 characters")
    private String sourceIp;

    /**
     * Device fingerprint or unique device identifier.
     * Optional — used to detect new device anomalies.
     * A transaction from an account's first-ever device
     * is a meaningful fraud signal.
     */
    @Size(max = 100, message = "Device ID must not exceed 100 characters")
    private String deviceId;
}