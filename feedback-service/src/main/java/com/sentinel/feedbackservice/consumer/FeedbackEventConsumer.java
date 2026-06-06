package com.sentinel.feedbackservice.consumer;

import com.sentinel.sentinelcommons.event.FeedbackEvent;
import com.sentinel.feedbackservice.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the model.feedback topic.
 *
 * Final consumer in the Sentinel pipeline.
 * Receives analyst decisions from case-management-service
 * and delegates to FeedbackService for processing.
 *
 * This closes the learning loop:
 * Transaction submitted → rules evaluated → AI scored
 * → case created → analyst decides → feedback processed
 *
 * The feedback processed here is the signal that improves
 * Sentinel over time. Every CONFIRMED_FRAUD teaches the
 * system what real fraud looks like. Every FALSE_POSITIVE
 * teaches it what legitimate transactions look like.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedbackEventConsumer {

    private final FeedbackService feedbackService;

    @KafkaListener(
            topics = "${sentinel.kafka.topics.model-feedback}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            ConsumerRecord<String, FeedbackEvent> record,
            Acknowledgment ack) {

        FeedbackEvent event = record.value();

        log.info("FeedbackEvent received — " +
                        "transaction: {}, case: {}, " +
                        "decision: {}, partition: {}, offset: {}",
                event.getTransactionId(),
                event.getCaseId(),
                event.getAnalystDecision(),
                record.partition(),
                record.offset());

        try {
            feedbackService.processFeedback(event);
            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Failed to process feedback — " +
                            "transaction: {}, error: {}",
                    event.getTransactionId(),
                    ex.getMessage(), ex);
        }
    }
}