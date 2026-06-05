package com.sentinel.scoringservice.prompt;

import com.sentinel.sentinelcommons.event.FraudScoredEvent;
import org.springframework.stereotype.Component;

/**
 * Builds the fraud scoring prompt sent to Groq AI.
 *
 * Prompt engineering principles applied here:
 *
 * 1. ROLE DEFINITION
 * Tell the AI exactly what it is and what expertise
 * it should apply. "You are a senior fraud analyst"
 * produces better results than an open-ended question.
 *
 * 2. STRUCTURED CONTEXT
 * Provide all relevant information in a clear format.
 * The AI cannot assess what it does not know.
 * We give it: transaction details, rules results,
 * risk score, triggered rules, and explanations.
 *
 * 3. CONSTRAINED OUTPUT FORMAT
 * Specify exactly how the response should be formatted.
 * This makes parsing reliable. Without this the AI
 * might write an essay when we need structured data.
 *
 * 4. TEMPERATURE ALIGNMENT
 * We set temperature=0.1 in application.properties.
 * Low temperature + constrained format = consistent,
 * parseable responses every time.
 *
 * 5. FRAUD-SPECIFIC CONTEXT
 * The prompt includes domain knowledge — what kinds
 * of patterns are suspicious in the Nigerian/African
 * fintech context. This grounds the AI's reasoning
 * in relevant fraud patterns.
 */
@Component
public class FraudScoringPrompt {

    /**
     * Builds the complete prompt for a fraud assessment.
     *
     * The prompt has four sections:
     * 1. Role — who the AI is
     * 2. Context — what fraud patterns to consider
     * 3. Transaction data — the specific transaction
     * 4. Output format — exactly how to respond
     *
     * @param event the scored event from the rules engine
     * @return complete prompt string ready for Groq
     */
    public String buildPrompt(FraudScoredEvent event) {
        return """
                You are a senior fraud analyst at a fintech company \
                operating in West Africa and internationally. \
                Your expertise covers digital payment fraud, \
                account takeover, money mule detection, and \
                transaction pattern analysis.
                
                FRAUD CONTEXT:
                You are evaluating transactions for a real-time fraud \
                detection system. The rules engine has already applied \
                automated checks. Your role is to provide a deeper \
                contextual assessment based on the full transaction \
                profile and the rules that fired.
                
                Common fraud patterns in this context:
                - Account takeover: new device + unusual amount + off-hours
                - Money mule: recipient with fraud history + round amounts
                - Card testing: multiple small transactions in rapid succession
                - Geographic spoofing: impossible travel between transactions
                - Social engineering: sudden large transfer to new recipient
                - Crypto cash-out: transfer to crypto exchange after account compromise
                
                TRANSACTION DETAILS:
                Transaction ID: %s
                Account ID: %s
                Merchant/Recipient: %s
                Amount: %s
                Rules Engine Score: %d/100
                Risk Level from Rules: %s
                Current Status: %s
                
                RULES THAT FIRED:
                %s
                
                RULES ENGINE EXPLANATION:
                %s
                
                ASSESSMENT TASK:
                Based on the transaction details and the rules that fired, \
                provide a fraud risk assessment. Consider:
                1. Do the fired rules together suggest a coherent fraud pattern?
                2. Is the combination of signals consistent with known fraud types?
                3. Are there any mitigating factors that suggest legitimacy?
                4. How confident are you in this assessment?
                
                REQUIRED RESPONSE FORMAT (respond with EXACTLY this structure):
                AI_SCORE: [number 0-100]
                RISK_ASSESSMENT: [LOW/MEDIUM/HIGH/CRITICAL] - [one sentence summary]
                RECOMMENDATION: [APPROVE/FLAG/BLOCK]
                REASONING: [2-3 sentences explaining your assessment]
                
                Respond with ONLY the four lines above. No preamble, \
                no additional text.
                """.formatted(
                event.getTransactionId(),
                event.getAccountId(),
                event.getMerchantId() != null
                        ? event.getMerchantId() : "UNKNOWN",
                event.getAccountId(),
                event.getFraudScore(),
                event.getRiskLevel(),
                event.getStatus(),
                event.getTriggeredRules() != null
                        && !event.getTriggeredRules().isEmpty()
                        ? String.join("\n- ",
                        event.getTriggeredRules())
                        : "None",
                event.getAiExplanation() != null
                        ? event.getAiExplanation()
                        : "No explanation available"
        );
    }
}