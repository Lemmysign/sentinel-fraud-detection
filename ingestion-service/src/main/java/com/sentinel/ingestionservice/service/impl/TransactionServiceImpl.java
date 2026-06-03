package com.sentinel.ingestionservice.service.impl;

import com.sentinel.ingestionservice.service.TransactionService;
import com.sentinel.sentinelcommons.event.TransactionEvent;
import com.sentinel.ingestionservice.dto.TransactionRequest;
import com.sentinel.ingestionservice.dto.TransactionResponse;
import com.sentinel.ingestionservice.mapper.TransactionMapper;
import com.sentinel.ingestionservice.validator.TransactionValidator;
import com.sentinel.sentinelcommons.enums.TransactionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of TransactionService.
 *
 * Responsibilities:
 * 1. Validate the transaction (business rules)
 * 2. Map request to Kafka event
 * 3. Publish to Kafka asynchronously
 * 4. Return acknowledgement immediately
 *
 * Concurrency design:
 * KafkaTemplate.send() is non-blocking — it returns a
 * CompletableFuture immediately. We attach callbacks to
 * handle success and failure without blocking the
 * request thread. This means thousands of concurrent
 * requests can be in flight simultaneously without
 * thread exhaustion.
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;
    private final TransactionMapper transactionMapper;
    private final TransactionValidator transactionValidator;

    /**
     * Topic name injected from application.properties.
     * Using @Value means we can change the topic name
     * without touching code — just update config.
     */
    @Value("${sentinel.kafka.topics.transactions-raw}")
    private String transactionsRawTopic;

    /**
     * Submits a transaction to the fraud detection pipeline.
     *
     * Flow:
     * 1. Validate business rules
     * 2. Map to Kafka event (generates transaction ID here)
     * 3. Publish to Kafka — non-blocking
     * 4. Return acknowledgement immediately
     *
     * The caller gets a response in milliseconds.
     * The actual fraud assessment happens asynchronously
     * across the downstream services.
     */
    @Override
    public TransactionResponse submitTransaction(TransactionRequest request) {

        // Step 1 — Business validation
        // Throws TransactionException if rules are violated
        // Caught by GlobalExceptionHandler → 400 response
        transactionValidator.validate(request);

        // Step 2 — Map to event
        // transactionId and timestamp are generated here
        TransactionEvent event = transactionMapper.toEvent(request);

        log.info("Submitting transaction to pipeline — id: {}, account: {}, amount: {} {}",
                event.getTransactionId(),
                event.getAccountId(),
                event.getAmount(),
                event.getCurrency());

        // Step 3 — Publish to Kafka
        // The transaction ID is used as the Kafka message key.
        // Using the same key for all events of one transaction
        // guarantees they go to the same partition — preserving
        // order across the pipeline.
        publishToKafka(event);

        // Step 4 — Return immediate acknowledgement
        // We do not wait for Kafka confirmation before responding.
        // The send is fire-and-forget with async error handling.
        return TransactionResponse.builder()
                .transactionId(event.getTransactionId())
                .status(TransactionStatus.RECEIVED)
                .message("Transaction received and submitted for fraud assessment")
                .receivedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Publishes the event to Kafka asynchronously.

     * KafkaTemplate.send() returns a CompletableFuture.
     * We attach whenComplete() to log success or failure
     * without blocking the calling thread.

     * Why non-blocking?
     * A typical Kafka publish takes 5-50ms. If we blocked
     * the request thread waiting for Kafka confirmation,
     * a service handling 1000 req/sec would need 1000
     * threads just waiting. Non-blocking means one thread
     * can initiate thousands of sends and handle the
     * results as they complete.

     * This is particularly important to Java 21 virtual
     * threads — they are cheap, but we still avoid
     * unnecessary blocking.
     */
    private void publishToKafka(TransactionEvent event) {
        CompletableFuture<SendResult<String, TransactionEvent>> future =
                kafkaTemplate.send(
                        transactionsRawTopic,
                        event.getTransactionId(), // message key
                        event                      // message value
                );

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug(
                        "Transaction published to Kafka — id: {}, " +
                                "topic: {}, partition: {}, offset: {}",
                        event.getTransactionId(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset()
                );
            } else {
                log.error(
                        "Failed to publish transaction to Kafka — id: {}, error: {}",
                        event.getTransactionId(),
                        ex.getMessage(),
                        ex
                );
                // In production this would trigger a retry mechanism
                // or write to a dead letter queue for reprocessing
            }
        });
    }
}