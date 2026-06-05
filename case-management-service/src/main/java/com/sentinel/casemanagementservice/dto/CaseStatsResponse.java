package com.sentinel.casemanagementservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dashboard statistics for the analyst UI.
 *
 * Returned by GET /api/v1/cases/stats
 *
 * Gives a real-time overview of the case queue.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseStatsResponse {

    /** Total cases in the system */
    private long totalCases;

    /** Cases awaiting analyst review */
    private long flaggedCases;

    /** Cases automatically blocked */
    private long blockedCases;

    /** Cases resolved by analysts */
    private long resolvedCases;

    /** CRITICAL risk level cases */
    private long criticalCases;

    /** HIGH risk level cases */
    private long highRiskCases;
}