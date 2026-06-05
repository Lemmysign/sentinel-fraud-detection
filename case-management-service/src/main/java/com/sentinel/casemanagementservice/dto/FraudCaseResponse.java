package com.sentinel.casemanagementservice.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.sentinel.sentinelcommons.enums.FraudDecision;
import com.sentinel.sentinelcommons.enums.RiskLevel;
import com.sentinel.sentinelcommons.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbound DTO for fraud case API responses.
 *
 * Never expose JPA entities directly through REST APIs.
 * Reasons:
 * 1. Entities may contain internal fields (version,
 *    internal IDs) that clients should not see
 * 2. Lazy-loaded JPA relationships can cause
 *    serialization errors outside transactions
 * 3. Changing the entity would change the API contract
 *
 * This DTO is the stable public contract — the entity
 * can change internally without affecting API consumers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCaseResponse {

    private String caseId;
    private String transactionId;
    private String accountId;
    private String merchantId;
    private int fraudScore;
    private RiskLevel riskLevel;
    private TransactionStatus status;
    private FraudDecision analystDecision;
    private List<String> triggeredRules;
    private String aiExplanation;
    private String analystNotes;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime resolvedAt;
}