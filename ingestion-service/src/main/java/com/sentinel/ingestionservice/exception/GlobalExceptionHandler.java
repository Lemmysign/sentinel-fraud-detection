package com.sentinel.ingestionservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the ingestion service.
 *
 * @RestControllerAdvice tells Spring Boot to apply this
 * handler to ALL controllers in this service. Every
 * exception that bubbles up from any controller or
 * service method is caught here.

 * Why centralize exception handling?
 * Without this, every controller method would need
 * its own try/catch block. Centralizing means:
 * - Consistent error response format across all endpoints
 * - Error logging in one place
 * - No duplicated error handling code
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles @Valid annotation failures.

     * When a TransactionRequest fails field validation
     * (e.g. blank accountId, invalid currency format),
     * Spring throws MethodArgumentNotValidException.

     * We catch it here and return a map of
     * field name → error message so the caller knows
     * exactly which fields are invalid.

     * Example response:
     * {
     *   "accountId": "Account ID is required",
     *   "currency": "Currency must be a valid 3-letter ISO 4217 code"
     * }
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            fieldErrors.put(fieldName, message);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("status", "VALIDATION_FAILED");
        response.put("errors", fieldErrors);
        response.put("timestamp", LocalDateTime.now().toString());

        log.warn("Validation failed for transaction request: {}", fieldErrors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    /**
     * Handles business rule violations.

     * Thrown by TransactionValidator or service layer
     * when a transaction fails business checks beyond
     * simple field validation.
     */
    @ExceptionHandler(TransactionException.class)
    public ResponseEntity<Map<String, Object>> handleTransactionException(
            TransactionException ex) {

        Map<String, Object> response = new HashMap<>();
        response.put("status", "REJECTED");
        response.put("errorCode", ex.getErrorCode());
        response.put("message", ex.getMessage());
        response.put("transactionId", ex.getTransactionId());
        response.put("timestamp", LocalDateTime.now().toString());

        log.error("Transaction rejected — errorCode: {}, message: {}",
                ex.getErrorCode(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    /**
     * Catch-all handler for unexpected exceptions.

     * If something unexpected happens (database error,
     * Kafka unavailable, null pointer), we catch it here
     * and return a generic 500 response.

     * We deliberately do NOT expose the exception details
     * to the caller — internal error details should never
     * leak to external clients. We log the full stack
     * trace internally for debugging.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex) {

        Map<String, Object> response = new HashMap<>();
        response.put("status", "ERROR");
        response.put("message", "An internal error occurred. Please try again.");
        response.put("timestamp", LocalDateTime.now().toString());

        log.error("Unexpected error in ingestion service", ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}