package com.sentinel.casemanagementservice.service;

import com.sentinel.sentinelcommons.event.FraudAlertEvent;
import com.sentinel.casemanagementservice.dto.CaseDecisionRequest;
import com.sentinel.casemanagementservice.dto.CaseStatsResponse;
import com.sentinel.casemanagementservice.dto.FraudCaseResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Contract for fraud case management operations.
 */
public interface FraudCaseService {

    /**
     * Creates a new fraud case from a Kafka alert event.
     * Called by the Kafka consumer.
     */
    void createCase(FraudAlertEvent event);

    /**
     * Retrieves a single case by ID.
     */
    FraudCaseResponse getCaseById(String caseId);

    /**
     * Retrieves all cases with pagination.
     */
    Page<FraudCaseResponse> getAllCases(Pageable pageable);

    /**
     * Retrieves cases filtered by account ID.
     */
    Page<FraudCaseResponse> getCasesByAccount(
            String accountId, Pageable pageable);

    /**
     * Records an analyst decision on a case.
     * Publishes feedback event to Kafka.
     * Updates recipient risk counter in Redis.
     */
    FraudCaseResponse recordDecision(
            String caseId,
            CaseDecisionRequest request);

    /**
     * Returns dashboard statistics.
     */
    CaseStatsResponse getStats();
}