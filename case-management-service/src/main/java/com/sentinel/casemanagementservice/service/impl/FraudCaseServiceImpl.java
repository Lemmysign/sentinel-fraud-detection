package com.sentinel.casemanagementservice.service.impl;

import com.sentinel.sentinelcommons.RedisKeys;
import com.sentinel.sentinelcommons.enums.FraudDecision;
import com.sentinel.sentinelcommons.enums.RiskLevel;
import com.sentinel.sentinelcommons.enums.TransactionStatus;
import com.sentinel.sentinelcommons.event.FeedbackEvent;
import com.sentinel.sentinelcommons.event.FraudAlertEvent;
import com.sentinel.casemanagementservice.dto.CaseDecisionRequest;
import com.sentinel.casemanagementservice.dto.CaseStatsResponse;
import com.sentinel.casemanagementservice.dto.FraudCaseResponse;
import com.sentinel.casemanagementservice.exception.CaseNotFoundException;
import com.sentinel.casemanagementservice.mapper.FraudCaseMapper;
import com.sentinel.casemanagementservice.model.FraudCase;
import com.sentinel.casemanagementservice.repository.FraudCaseRepository;
import com.sentinel.casemanagementservice.service.FraudCaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Implementation of fraud case management business logic.
 *
 * Transaction management:
 * @Transactional on write methods ensures database
 * operations are atomic. If the Kafka publish fails
 * after saving to PostgreSQL, the transaction rolls back
 * — preventing orphaned cases with no feedback event.
 *
 * @Transactional(readOnly=true) on read methods skips
 * Hibernate dirty checking — meaningful performance
 * improvement for high-frequency read operations.
 *
 * Redis integration:
 * When fraud is CONFIRMED, the recipient risk counter
 * is incremented. This is best-effort — a Redis failure
 * does not roll back the case decision. The case decision
 * is the source of truth; Redis is a derived cache.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudCaseServiceImpl implements FraudCaseService {

    private final FraudCaseRepository fraudCaseRepository;
    private final FraudCaseMapper fraudCaseMapper;
    private final KafkaTemplate<String, FeedbackEvent>
            kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${sentinel.kafka.topics.model-feedback}")
    private String modelFeedbackTopic;

    /**
     * Creates a fraud case from a Kafka alert event.
     *
     * Idempotent — checks if a case already exists for
     * this transaction before creating. Prevents duplicate
     * cases if the consumer retries due to a failure.
     */
    @Override
    @Transactional
    public void createCase(FraudAlertEvent event) {

        // Idempotency check — prevent duplicate cases
        if (fraudCaseRepository.existsByTransactionId(
                event.getTransactionId())) {
            log.warn("Case already exists for transaction: " +
                            "{} — skipping duplicate",
                    event.getTransactionId());
            return;
        }

        FraudCase fraudCase =
                fraudCaseMapper.toEntity(event);
        fraudCaseRepository.save(fraudCase);

        log.info("Fraud case created — case: {}, " +
                        "transaction: {}, account: {}, " +
                        "score: {}, risk: {}",
                fraudCase.getCaseId(),
                fraudCase.getTransactionId(),
                fraudCase.getAccountId(),
                fraudCase.getFraudScore(),
                fraudCase.getRiskLevel());
    }

    /**
     * Retrieves a single fraud case by case ID.
     */
    @Override
    @Transactional(readOnly = true)
    public FraudCaseResponse getCaseById(String caseId) {
        FraudCase fraudCase = fraudCaseRepository
                .findById(caseId)
                .orElseThrow(() ->
                        new CaseNotFoundException(caseId));

        return fraudCaseMapper.toResponse(fraudCase);
    }

    /**
     * Retrieves all cases with pagination.
     * Ordered by creation date — most recent first.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<FraudCaseResponse> getAllCases(
            Pageable pageable) {
        return fraudCaseRepository
                .findAll(pageable)
                .map(fraudCaseMapper::toResponse);
    }

    /**
     * Retrieves all cases for a specific account.
     * Ordered by creation date — most recent first.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<FraudCaseResponse> getCasesByAccount(
            String accountId, Pageable pageable) {
        return fraudCaseRepository
                .findByAccountIdOrderByCreatedAtDesc(
                        accountId, pageable)
                .map(fraudCaseMapper::toResponse);
    }

    /**
     * Records an analyst decision on a fraud case.
     *
     * Three things happen atomically in one transaction:
     * 1. Update case with decision and timestamp
     * 2. Publish FeedbackEvent to Kafka
     * 3. If fraud confirmed — increment Redis counter
     *
     * The @Transactional annotation ensures that if
     * the Kafka publish fails, the database update
     * also rolls back. Consistency is maintained.
     */
    @Override
    @Transactional
    public FraudCaseResponse recordDecision(
            String caseId,
            CaseDecisionRequest request) {

        FraudCase fraudCase = fraudCaseRepository
                .findById(caseId)
                .orElseThrow(() ->
                        new CaseNotFoundException(caseId));

        // Update case with analyst decision
        fraudCase.setAnalystDecision(request.getDecision());
        fraudCase.setAnalystNotes(request.getNotes());
        fraudCase.setResolvedAt(LocalDateTime.now());

        // Update status based on decision
        if (request.getDecision() ==
                FraudDecision.CONFIRMED_FRAUD) {
            fraudCase.setStatus(TransactionStatus.BLOCKED);
        } else if (request.getDecision() ==
                FraudDecision.FALSE_POSITIVE) {
            fraudCase.setStatus(TransactionStatus.APPROVED);
        }
        // UNDER_REVIEW keeps current status

        FraudCase saved =
                fraudCaseRepository.save(fraudCase);

        log.info("Case decision recorded — case: {}, " +
                        "decision: {}, status: {}",
                caseId,
                request.getDecision(),
                saved.getStatus());

        // Publish feedback event to close learning loop
        publishFeedbackEvent(saved);

        // If confirmed fraud — increment recipient risk
        // counter in Redis so future transactions to this
        // merchant score higher automatically
        if (request.getDecision() ==
                FraudDecision.CONFIRMED_FRAUD) {
            incrementRecipientRiskCounter(
                    saved.getMerchantId());
        }

        return fraudCaseMapper.toResponse(saved);
    }

    /**
     * Returns dashboard statistics about the case queue.
     */
    @Override
    @Transactional(readOnly = true)
    public CaseStatsResponse getStats() {
        long total = fraudCaseRepository.count();
        long flagged = fraudCaseRepository
                .countByStatus(TransactionStatus.FLAGGED);
        long blocked = fraudCaseRepository
                .countByStatus(TransactionStatus.BLOCKED);
        long approved = fraudCaseRepository
                .countByStatus(TransactionStatus.APPROVED);
        long critical = fraudCaseRepository
                .countByRiskLevel(RiskLevel.CRITICAL);
        long high = fraudCaseRepository
                .countByRiskLevel(RiskLevel.HIGH);

        return CaseStatsResponse.builder()
                .totalCases(total)
                .flaggedCases(flagged)
                .blockedCases(blocked)
                .resolvedCases(approved)
                .criticalCases(critical)
                .highRiskCases(high)
                .build();
    }

    /**
     * Publishes a FeedbackEvent to the model.feedback topic.
     * The feedback service consumes this to close the
     * learning loop — analyst decisions teach the AI
     * what real fraud looks like.
     */
    private void publishFeedbackEvent(FraudCase fraudCase) {
        FeedbackEvent feedbackEvent = FeedbackEvent.builder()
                .transactionId(fraudCase.getTransactionId())
                .caseId(fraudCase.getCaseId())
                .accountId(fraudCase.getAccountId())
                .originalRiskLevel(fraudCase.getRiskLevel())
                .originalFraudScore(fraudCase.getFraudScore())
                .analystDecision(fraudCase.getAnalystDecision())
                .analystNotes(fraudCase.getAnalystNotes())
                .decidedAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(
                modelFeedbackTopic,
                feedbackEvent.getTransactionId(),
                feedbackEvent
        ).whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("FeedbackEvent published — " +
                                "transaction: {}, decision: {}",
                        feedbackEvent.getTransactionId(),
                        feedbackEvent.getAnalystDecision());
            } else {
                log.error("Failed to publish FeedbackEvent" +
                                " — transaction: {}, error: {}",
                        feedbackEvent.getTransactionId(),
                        ex.getMessage());
            }
        });
    }

    /**
     * Increments the recipient risk counter in Redis.
     *
     * This creates the feedback loop between case management
     * and the rules engine:
     * Fraud confirmed → counter increments
     * → next transaction to same merchant triggers
     *   RecipientRiskRule automatically
     *
     * Best-effort — Redis failure is logged but does not
     * fail the transaction. The case decision is already
     * saved in PostgreSQL.
     */
    private void incrementRecipientRiskCounter(
            String merchantId) {
        if (merchantId == null || merchantId.isBlank()) {
            return;
        }

        try {
            String key = RedisKeys.RECIPIENT_RISK_PREFIX
                    + merchantId;
            Long newCount = redisTemplate
                    .opsForValue().increment(key);

            log.info("Recipient risk counter incremented — " +
                            "merchant: {}, new count: {}",
                    merchantId, newCount);
        } catch (Exception e) {
            log.error("Failed to increment recipient risk " +
                            "counter — merchant: {}, error: {}",
                    merchantId, e.getMessage());
        }
    }
}