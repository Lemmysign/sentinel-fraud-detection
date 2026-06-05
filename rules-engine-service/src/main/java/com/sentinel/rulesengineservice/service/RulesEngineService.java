package com.sentinel.rulesengineservice.service;



import com.sentinel.sentinelcommons.event.TransactionEvent;

/**
 * Contract for rules engine operations.
 *
 * Separating interface from implementation allows
 * the Kafka consumer to depend on the abstraction,
 * not the concrete implementation — making the
 * consumer easy to unit test by mocking this interface
 * without needing a real Kafka broker or Redis instance.
 */
public interface RulesEngineService {

    /**
     * Evaluates a transaction against all fraud rules
     * and publishes the scored result to the
     * fraud.scored Kafka topic.
     *
     * @param transaction the transaction consumed from
     *                    the transactions.raw topic
     */
    void evaluateAndPublish(TransactionEvent transaction);
}