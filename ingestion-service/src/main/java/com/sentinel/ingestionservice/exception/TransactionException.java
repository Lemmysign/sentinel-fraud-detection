package com.sentinel.ingestionservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a transaction fails business validation
 * rules that go beyond basic field validation.
 *
 * Examples:
 * - Duplicate transaction ID detected
 * - Account on sanctions list
 * - Transaction amount exceeds account limit
 *
 * The @ResponseStatus annotation maps this exception
 * to a 400 Bad Request HTTP response automatically
 * when thrown from a controller or service.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class TransactionException extends RuntimeException {

    private final String transactionId;
    private final String errorCode;

    public TransactionException(String message, String errorCode) {
        super(message);
        this.transactionId = null;
        this.errorCode = errorCode;
    }

    public TransactionException(String message,
                                String transactionId,
                                String errorCode) {
        super(message);
        this.transactionId = transactionId;
        this.errorCode = errorCode;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getErrorCode() {
        return errorCode;
    }
}