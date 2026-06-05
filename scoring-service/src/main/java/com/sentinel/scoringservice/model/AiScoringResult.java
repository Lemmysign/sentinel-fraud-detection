package com.sentinel.scoringservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Structured result parsed from Groq AI response.
 *
 * We ask Groq to respond in a specific format so
 * we can parse it reliably. The parser extracts
 * these fields from the AI's text response.
 *
 * Why parse instead of using function calling?
 * Groq supports structured outputs but parsing
 * a clearly formatted text response is simpler,
 * more resilient to model variation, and easier
 * to debug when something goes wrong.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiScoringResult {

    /**
     * AI fraud score — 0 to 100.
     * 0  = AI considers this completely legitimate
     * 100 = AI considers this definitively fraudulent
     */
    private int aiScore;

    /**
     * AI risk assessment in plain English.
     * Stored on the fraud case for analyst review.
     * Example: "HIGH - Multiple indicators suggest
     * account takeover attempt"
     */
    private String riskAssessment;

    /**
     * Detailed reasoning from the AI.
     * Explains WHY it assigned this score.
     * Invaluable for analysts reviewing flagged cases.
     */
    private String reasoning;

    /**
     * AI recommendation for action.
     * APPROVE, FLAG, or BLOCK
     */
    private String recommendation;

    /**
     * Whether the AI parsing succeeded.
     * If false, we fall back to rules engine score only.
     */
    private boolean parsedSuccessfully;
}