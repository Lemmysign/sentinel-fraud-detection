package com.sentinel.sentinelcommons.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sentinel.sentinelcommons.enums.FraudDecision;
import com.sentinel.sentinelcommons.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackEvent {

    private String transactionId;
    private String caseId;
    private String accountId;

    /**
     * The original AI risk level for this transaction.
     * Compared with the analyst's decision to measure
     * model accuracy over time.
     */
    private RiskLevel originalRiskLevel;

    /**
     * The original fraud score (0-100).
     */
    private int originalFraudScore;

    /**
     * What the analyst actually decided.
     * CONFIRMED_FRAUD or FALSE_POSITIVE tells us
     * whether the model was right or wrong.
     */
    private FraudDecision analystDecision;

    /**
     * Optional notes from the analyst explaining
     * their decision. Valuable training signal.
     */
    private String analystNotes;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime decidedAt;
}