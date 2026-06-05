package com.sentinel.casemanagementservice.mapper;

import com.sentinel.sentinelcommons.event.FraudAlertEvent;
import com.sentinel.sentinelcommons.enums.TransactionStatus;
import com.sentinel.casemanagementservice.dto.FraudCaseResponse;
import com.sentinel.casemanagementservice.model.FraudCase;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Converts between FraudCase entity, FraudAlertEvent,
 * and FraudCaseResponse DTO.
 *
 * Triggered rules are stored as comma-separated string
 * in the database (simple, no extra table needed for
 * a list of strings) and converted back to List<String>
 * when building the response DTO.
 */
@Component
public class FraudCaseMapper {

    /**
     * Creates a new FraudCase entity from a FraudAlertEvent.
     * Called when the consumer receives a new fraud alert.
     */
    public FraudCase toEntity(FraudAlertEvent event) {
        return FraudCase.builder()
                .caseId(event.getCaseId())
                .transactionId(event.getTransactionId())
                .accountId(event.getAccountId())
                .merchantId(event.getMerchantId())  // ← correct
                .fraudScore(event.getFraudScore())
                .riskLevel(event.getRiskLevel())
                .status(TransactionStatus.FLAGGED)
                .triggeredRules(
                        event.getTriggeredRules() != null
                                ? String.join(",",
                                event.getTriggeredRules())
                                : "")
                .aiExplanation(event.getAiExplanation())
                .createdAt(LocalDateTime.now())
                .version(0L)
                .build();
    }

    /**
     * Converts a FraudCase entity to a REST response DTO.
     */
    public FraudCaseResponse toResponse(FraudCase fraudCase) {
        return FraudCaseResponse.builder()
                .caseId(fraudCase.getCaseId())
                .transactionId(fraudCase.getTransactionId())
                .accountId(fraudCase.getAccountId())
                .merchantId(fraudCase.getMerchantId())
                .fraudScore(fraudCase.getFraudScore())
                .riskLevel(fraudCase.getRiskLevel())
                .status(fraudCase.getStatus())
                .analystDecision(fraudCase.getAnalystDecision())
                .triggeredRules(parseRules(
                        fraudCase.getTriggeredRules()))
                .aiExplanation(fraudCase.getAiExplanation())
                .analystNotes(fraudCase.getAnalystNotes())
                .createdAt(fraudCase.getCreatedAt())
                .resolvedAt(fraudCase.getResolvedAt())
                .build();
    }

    /**
     * Parses comma-separated rules string back to a List.
     * Returns empty list if null or blank.
     */
    private List<String> parseRules(String rulesStr) {
        if (rulesStr == null || rulesStr.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.asList(rulesStr.split(","));
    }
}