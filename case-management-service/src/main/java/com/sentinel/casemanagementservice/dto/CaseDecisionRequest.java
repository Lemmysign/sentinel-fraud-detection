package com.sentinel.casemanagementservice.dto;

import com.sentinel.sentinelcommons.enums.FraudDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inbound DTO for analyst case decisions.
 *
 * Sent to PUT /api/v1/cases/{caseId}/decision
 *
 * The analyst must provide a decision.
 * Notes are optional but strongly encouraged —
 * they improve the AI model's training signal.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseDecisionRequest {

    /**
     * The analyst's verdict.
     * CONFIRMED_FRAUD  → transaction was fraudulent
     * FALSE_POSITIVE   → transaction was legitimate
     * UNDER_REVIEW     → needs more investigation
     */
    @NotNull(message = "Decision is required")
    private FraudDecision decision;

    /**
     * Optional analyst notes explaining the decision.
     * These notes become training signal for the AI model.
     * Example: "Account holder confirmed they did not
     * initiate this transaction. Card likely skimmed."
     */
    @Size(max = 1000,
            message = "Notes must not exceed 1000 characters")
    private String notes;
}
