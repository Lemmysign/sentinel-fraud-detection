package com.sentinel.casemanagementservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested fraud case does not exist.
 * Maps to HTTP 404 Not Found.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class CaseNotFoundException extends RuntimeException {

    public CaseNotFoundException(String caseId) {
        super(String.format(
                "Fraud case not found: %s", caseId));
    }
}
