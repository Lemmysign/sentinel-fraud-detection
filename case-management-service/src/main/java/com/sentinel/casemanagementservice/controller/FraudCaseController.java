package com.sentinel.casemanagementservice.controller;

import com.sentinel.casemanagementservice.dto.CaseDecisionRequest;
import com.sentinel.casemanagementservice.dto.CaseStatsResponse;
import com.sentinel.casemanagementservice.dto.FraudCaseResponse;
import com.sentinel.casemanagementservice.service.FraudCaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for fraud case management.
 *
 * Exposes the analyst workflow:
 * - View all cases (paginated)
 * - View a specific case
 * - View cases for a specific account
 * - Record an analyst decision
 * - View dashboard statistics
 *
 * All endpoints are thin — they delegate immediately
 * to the service layer. No business logic here.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/cases")
@RequiredArgsConstructor
@Tag(
        name = "Fraud Case Management",
        description = "Analyst workflow for reviewing and " +
                "resolving fraud cases"
)
public class FraudCaseController {

    private final FraudCaseService fraudCaseService;

    /**
     * Get all fraud cases with pagination.
     * Default: page 0, size 20.
     */
    @GetMapping
    @Operation(summary = "Get all fraud cases",
            description = "Returns paginated list of " +
                    "all fraud cases")
    public ResponseEntity<Page<FraudCaseResponse>> getAllCases(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                fraudCaseService.getAllCases(pageable));
    }

    /**
     * Get a specific fraud case by ID.
     */
    @GetMapping("/{caseId}")
    @Operation(summary = "Get a fraud case by ID")
    public ResponseEntity<FraudCaseResponse> getCaseById(
            @PathVariable String caseId) {

        return ResponseEntity.ok(
                fraudCaseService.getCaseById(caseId));
    }

    /**
     * Get all cases for a specific account.
     * Used when investigating a customer's fraud history.
     */
    @GetMapping("/account/{accountId}")
    @Operation(summary = "Get cases for an account",
            description = "Returns all fraud cases " +
                    "associated with an account")
    public ResponseEntity<Page<FraudCaseResponse>>
    getCasesByAccount(
            @PathVariable String accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                fraudCaseService.getCasesByAccount(
                        accountId, pageable));
    }

    /**
     * Record an analyst decision on a case.
     *
     * This is the most important endpoint — it:
     * 1. Updates the case status in PostgreSQL
     * 2. Publishes FeedbackEvent to Kafka
     * 3. If fraud confirmed — increments Redis counter
     */
    @PutMapping("/{caseId}/decision")
    @Operation(
            summary = "Record analyst decision",
            description = "Records analyst verdict on a fraud " +
                    "case. Triggers feedback loop to " +
                    "improve AI model accuracy."
    )
    public ResponseEntity<FraudCaseResponse> recordDecision(
            @PathVariable String caseId,
            @Valid @RequestBody CaseDecisionRequest request) {

        log.info("Analyst decision received — case: {}, " +
                        "decision: {}",
                caseId, request.getDecision());

        return ResponseEntity.ok(
                fraudCaseService.recordDecision(
                        caseId, request));
    }


    @GetMapping("/stats")
    @Operation(summary = "Get case dashboard statistics")
    public ResponseEntity<CaseStatsResponse> getStats() {
        return ResponseEntity.ok(
                fraudCaseService.getStats());
    }
}