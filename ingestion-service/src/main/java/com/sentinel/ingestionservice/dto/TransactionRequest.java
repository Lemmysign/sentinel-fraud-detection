package com.sentinel.ingestionservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
**/


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
     * ISO 4217 currency code.
     * Case-insensitive — normalized to uppercase in mapper.
     * Examples: ngn, NGN, Ngn — all accepted.
     * Supported currencies validated in TransactionValidator.
     */
    @NotBlank(message = "Currency is required")
    @Size(max = 3, message = "Currency code must not exceed 3 characters")
    private String currency;

    /**
     * Type of transaction.
     * Case-insensitive — normalized to uppercase in mapper.
     * Allowed values: TRANSFER, PAYMENT, WITHDRAWAL, DEPOSIT
     * (any casing accepted — payment, Payment, PAYMENT all work)
     * Validated in TransactionValidator after normalization.
     */
    @NotBlank(message = "Transaction type is required")
    @Size(max = 20, message = "Transaction type must not exceed 20 characters")
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