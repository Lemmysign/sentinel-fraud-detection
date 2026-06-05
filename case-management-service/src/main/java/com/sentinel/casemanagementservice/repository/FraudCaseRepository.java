package com.sentinel.casemanagementservice.repository;

import com.sentinel.sentinelcommons.enums.RiskLevel;
import com.sentinel.sentinelcommons.enums.TransactionStatus;
import com.sentinel.casemanagementservice.model.FraudCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for FraudCase persistence operations.
 *
 * Extends JpaRepository which provides standard CRUD:
 * save(), findById(), findAll(), delete(), count()
 *
 * Custom queries are defined here for analyst-specific
 * access patterns. All queries use the indexes defined
 * on the FraudCase entity for optimal performance.
 *
 * Pagination:
 * All list queries accept Pageable for pagination.
 * An analyst dashboard showing ALL cases without
 * pagination would be unusable with millions of records.
 * Page size of 20 is the default in the controller.
 *
 * @Transactional(readOnly=true):
 * Read-only transactions skip Hibernate's dirty checking
 * (comparing every entity field against its snapshot
 * to detect changes). For read-heavy queries this is
 * a meaningful performance improvement.
 */
@Repository
public interface FraudCaseRepository
        extends JpaRepository<FraudCase, String> {

    /**
     * Find all cases for a specific account.
     * Used by analysts investigating a specific customer.
     * Ordered by creation date — most recent first.
     */
    Page<FraudCase> findByAccountIdOrderByCreatedAtDesc(
            String accountId, Pageable pageable);

    /**
     * Find all cases with a specific status.
     * Primary analyst workflow query:
     * "Show me all FLAGGED cases"
     * Uses idx_case_status index.
     */
    Page<FraudCase> findByStatusOrderByCreatedAtDesc(
            TransactionStatus status, Pageable pageable);

    /**
     * Find cases by status AND risk level.
     * Priority queue query:
     * "Show me FLAGGED CRITICAL cases first"
     * Uses idx_case_status_risk composite index.
     */
    Page<FraudCase> findByStatusAndRiskLevelOrderByCreatedAtDesc(
            TransactionStatus status,
            RiskLevel riskLevel,
            Pageable pageable);

    /**
     * Find a case by transaction ID.
     * Used when a downstream service needs to look up
     * a case by the original transaction reference.
     */
    Optional<FraudCase> findByTransactionId(
            String transactionId);

    /**
     * Count cases by status.
     * Used for dashboard statistics.
     */
    long countByStatus(TransactionStatus status);

    /**
     * Count cases by risk level.
     * Used for risk distribution statistics.
     */
    long countByRiskLevel(RiskLevel riskLevel);

    /**
     * Find unresolved HIGH and CRITICAL cases older
     * than a threshold — for SLA monitoring.
     *
     * An analyst dashboard should highlight cases that
     * have been waiting too long for review.
     *
     * Uses JPQL — Java Persistence Query Language.
     * Operates on entity field names not column names.
     */
    @Query("SELECT f FROM FraudCase f " +
            "WHERE f.status IN :statuses " +
            "AND f.riskLevel IN :riskLevels " +
            "AND f.createdAt < :threshold " +
            "ORDER BY f.riskLevel DESC, f.createdAt ASC")
    List<FraudCase> findUrgentUnresolvedCases(
            @Param("statuses") List<TransactionStatus> statuses,
            @Param("riskLevels") List<RiskLevel> riskLevels,
            @Param("threshold") LocalDateTime threshold);

    /**
     * Dashboard stats query — counts by status in one
     * database round trip instead of multiple queries.
     */
    @Query("SELECT f.status, COUNT(f) FROM FraudCase f " +
            "GROUP BY f.status")
    List<Object[]> countGroupByStatus();

    /**
     * Check if a case exists for a transaction.
     * Used by the consumer to prevent duplicate cases.
     */
    boolean existsByTransactionId(String transactionId);
}