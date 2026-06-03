package com.sentinel.sentinelcommons.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sentinel.sentinelcommons.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Kafka event published by case-management-service
 * to the fraud.alerts topic when a high-risk case
 * is created and requires immediate attention.

 * Consumed by:
 * - feedback-service (monitors alert outcomes)
 * - External notification systems (future)
  * Only published for HIGH and CRITICAL risk levels.
 * LOW and MEDIUM go through normal case flow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlertEvent {

    private String transactionId;
    private String caseId;
    private String accountId;
    private RiskLevel riskLevel;
    private int fraudScore;

    /**
     * Rules that contributed to this alert.
     */
    private List<String> triggeredRules;

    /**
     * AI explanation carried forward from scoring.
     */
    private String aiExplanation;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime alertCreatedAt;

}