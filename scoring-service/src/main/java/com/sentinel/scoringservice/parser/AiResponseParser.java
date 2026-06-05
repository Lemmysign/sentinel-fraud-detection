package com.sentinel.scoringservice.parser;

import com.sentinel.scoringservice.model.AiScoringResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Parses the structured text response from Groq AI
 * into an AiScoringResult object.
 *
 * Expected response format from Groq:
 * AI_SCORE: 75
 * RISK_ASSESSMENT: HIGH - Multiple indicators suggest account takeover
 * RECOMMENDATION: FLAG
 * REASONING: The combination of new device, off-hours timing, and...
 *
 * Parsing strategy:
 * Line-by-line parsing with prefix matching.
 * Simple and resilient — works even if the AI adds
 * minor formatting variations like extra spaces.
 *
 * Fallback behaviour:
 * If parsing fails for any reason, returns a result
 * with parsedSuccessfully=false. The service layer
 * falls back to the rules engine score alone.
 * The system never fails because of AI parsing issues.
 */
@Slf4j
@Component
public class AiResponseParser {

    public AiScoringResult parse(String aiResponse,
                                 String transactionId) {
        try {
            if (aiResponse == null || aiResponse.isBlank()) {
                log.warn("Empty AI response for " +
                        "transaction: {}", transactionId);
                return failedResult();
            }

            String[] lines = aiResponse.trim().split("\n");

            int aiScore = 50; // default if not parsed
            String riskAssessment = "UNKNOWN";
            String recommendation = "FLAG";
            String reasoning = "AI assessment unavailable";

            for (String line : lines) {
                line = line.trim();

                if (line.startsWith("AI_SCORE:")) {
                    aiScore = parseScore(
                            line.substring("AI_SCORE:".length())
                                    .trim(),
                            transactionId);

                } else if (line.startsWith("RISK_ASSESSMENT:")) {
                    riskAssessment = line
                            .substring("RISK_ASSESSMENT:".length())
                            .trim();

                } else if (line.startsWith("RECOMMENDATION:")) {
                    recommendation = line
                            .substring("RECOMMENDATION:".length())
                            .trim().toUpperCase();

                } else if (line.startsWith("REASONING:")) {
                    reasoning = line
                            .substring("REASONING:".length())
                            .trim();
                }
            }

            log.debug("AI response parsed — transaction: {}, " +
                            "score: {}, recommendation: {}",
                    transactionId, aiScore, recommendation);

            return AiScoringResult.builder()
                    .aiScore(aiScore)
                    .riskAssessment(riskAssessment)
                    .recommendation(recommendation)
                    .reasoning(reasoning)
                    .parsedSuccessfully(true)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse AI response — " +
                            "transaction: {}, error: {}",
                    transactionId, e.getMessage());
            return failedResult();
        }
    }

    /**
     * Parses the score string to an integer.
     * Clamps to 0-100 range.
     * Returns 50 (neutral) if parsing fails.
     */
    private int parseScore(String scoreStr,
                           String transactionId) {
        try {
            // Handle cases like "75" or "75/100"
            String cleaned = scoreStr.split("/")[0].trim();
            int score = Integer.parseInt(cleaned);
            return Math.max(0, Math.min(100, score));
        } catch (NumberFormatException e) {
            log.warn("Could not parse AI score '{}' for " +
                            "transaction: {} — defaulting to 50",
                    scoreStr, transactionId);
            return 50;
        }
    }

    /**
     * Returns a safe fallback result when parsing fails.
     * Score of 50 is neutral — does not artificially
     * inflate or deflate the final combined score.
     */
    private AiScoringResult failedResult() {
        return AiScoringResult.builder()
                .aiScore(50)
                .riskAssessment("UNKNOWN - AI parsing failed")
                .recommendation("FLAG")
                .reasoning("AI assessment could not be parsed. " +
                        "Defaulting to rules engine score.")
                .parsedSuccessfully(false)
                .build();
    }
}