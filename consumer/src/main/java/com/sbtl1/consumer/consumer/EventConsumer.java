package com.sbtl1.consumer.consumer;

import com.example.common.constants.KafkaConstants;
import com.example.common.exception.RetryableException;
import com.example.common.model.Event;
import com.example.common.model.ProcessingStep;
import com.example.common.model.StepStatus;
import com.sbtl1.consumer.service.EventProcessingService;
import com.sbtl1.consumer.service.ProcessedEventTracker;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer Component
 *
 * This class demonstrates several critical patterns for high-throughput Kafka consumers:
 * 1. Manual acknowledgment for precise offset control
 * 2. Error handling with retry and DLT patterns
 * 3. Asynchronous processing for improved throughput
 * 4. Stateful processing with step-based workflow
 * 5. Specialized handling for retry and DLT topics
 * 6. Distributed exactly-once processing with Redis
 */
@Component
@RequiredArgsConstructor
public class EventConsumer implements ConsumerSeekAware {

    private static final Logger log = LogManager.getLogger(EventConsumer.class);
    private final EventProcessingService eventProcessingService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ProcessedEventTracker processedEventTracker;

    /**
     * Consumes events from the main topic.
     *
     * CRITICAL SCENARIO: Manual Acknowledgment Pattern
     * - Uses manual acknowledgment for precise control over offset commits
     * - Implements "at-most-once" delivery by acknowledging before processing
     * - Alternative: implement "at-least-once" by acknowledging after processing
     * - Alternative: implement "exactly-once" with transactional outbox pattern
     *
     * CRITICAL SCENARIO: Asynchronous Processing
     * - Processes events asynchronously to improve throughput
     * - Doesn't block the consumer thread during long-running operations
     * - Allows the consumer to continue polling for new messages
     *
     * @param record The Kafka record containing the event
     * @param acknowledgment The acknowledgment callback
     */
    @KafkaListener(topics = KafkaConstants.TOPIC_NAME, groupId = KafkaConstants.GROUP_ID)
    public void consumeEvent(ConsumerRecord<String, Event> record, Acknowledgment acknowledgment) {
        Event event = record.value();
        log.info(
                "Received event: {} from topic: {}, partition: {}, offset: {}",
                event.getId(),
                record.topic(),
                record.partition(),
                record.offset());

        // Extract headers for tracing and debugging
        record.headers().forEach(header -> log.debug("Header: {} = {}", header.key(), new String(header.value())));

        try {
            // CRITICAL SCENARIO: At-Most-Once Delivery Pattern
            // Acknowledge the message immediately to prevent redelivery
            // This implements an "at-most-once" delivery pattern
            // If processing fails, the message won't be reprocessed
            acknowledgment.acknowledge();

            // CRITICAL SCENARIO: Distributed Exactly-Once Processing
            // Check if we've already processed this event using Redis with TTL
            String eventKey = event.getId().toString();

            // If the event is already fully processed, skip it
            if (processedEventTracker.isEventProcessed(eventKey)) {
                log.info("Event {} already fully processed, skipping", event.getId());
                return;
            }

            // If the event is currently being processed (in-progress), skip it
            // This handles the case where the same message is delivered multiple times
            // while it's still being processed
            if (processedEventTracker.isEventBeingProcessed(eventKey)) {
                log.info("Event {} is currently being processed, skipping duplicate", event.getId());
                return;
            }

            // Mark the event as being processed in Redis with TTL
            // This is an atomic operation that prevents race conditions
            if (!processedEventTracker.markEventAsProcessing(eventKey)) {
                log.info("Event {} was marked as processing by another instance, skipping", event.getId());
                return;
            }

            // CRITICAL SCENARIO: Asynchronous Processing Pattern
            // Process the event asynchronously without waiting for completion
            // This improves throughput by not blocking the consumer thread
            eventProcessingService
                    .processEvent(event)
                    .thenAccept(processedEvent -> {
                        if (processedEvent.isProcessed()) {
                            // CRITICAL SCENARIO: Successful Processing
                            log.info("Event processed successfully: {}", processedEvent.getId());
                            // Mark as successfully processed in Redis with TTL
                            processedEventTracker.markEventAsProcessed(eventKey);
                        } else {
                            // CRITICAL SCENARIO: Failed Processing to DLT
                            // If processing failed but it's not retryable, send to DLT
                            log.error("Event processing failed: {}", processedEvent.getId());
                            // Remove from processing tracker to allow retries
                            processedEventTracker.removeEvent(eventKey);
                            kafkaTemplate.send(
                                    KafkaConstants.DLT_TOPIC_NAME,
                                    processedEvent.getId().toString(),
                                    processedEvent);
                        }
                    })
                    .exceptionally(e -> {
                        Throwable cause = e.getCause();
                        if (cause instanceof RetryableException) {
                            // CRITICAL SCENARIO: Retry Pattern
                            RetryableException re = (RetryableException) cause;
                            ProcessingStep failedStep = re.getFailedStep();

                            log.warn(
                                    "Retryable error occurred for event: {} at step: {} (attempt {})",
                                    event.getId(),
                                    failedStep != null ? failedStep.name() : "unknown",
                                    re.getAttempts());

                            // Remove from processing tracker to allow retries
                            processedEventTracker.removeEvent(eventKey);

                            // CRITICAL SCENARIO: Retry Topic Pattern
                            // If we haven't exceeded max retries, send to retry topic
                            if (re.getAttempts() < KafkaConstants.MAX_RETRY_ATTEMPTS) {
                                // The current step is already set in the event
                                kafkaTemplate.send(
                                        KafkaConstants.RETRY_TOPIC_NAME,
                                        event.getId().toString(),
                                        event);
                            } else {
                                // CRITICAL SCENARIO: Dead Letter Topic Pattern
                                // Otherwise, send to DLT after max retries
                                event.setErrorMessage("Max retry attempts exceeded for step "
                                        + (failedStep != null ? failedStep.name() : "unknown")
                                        + ": "
                                        + e.getMessage());
                                kafkaTemplate.send(
                                        KafkaConstants.DLT_TOPIC_NAME,
                                        event.getId().toString(),
                                        event);
                            }
                        } else {
                            // CRITICAL SCENARIO: Non-Retryable Errors
                            // Non-retryable error, send to DLT
                            log.error("Non-retryable error occurred for event: {}", event.getId(), e);
                            // Remove from processing tracker to allow retries
                            processedEventTracker.removeEvent(eventKey);
                            event.setErrorMessage("Non-retryable error: " + e.getMessage());
                            kafkaTemplate.send(
                                    KafkaConstants.DLT_TOPIC_NAME, event.getId().toString(), event);
                        }
                        return null;
                    });
        } catch (Exception e) {
            // CRITICAL SCENARIO: Exception Handling
            // Handle exceptions that occur before processing starts
            log.error("Error submitting event for processing: {}", event.getId(), e);
            // Remove from processing tracker to allow retries
            processedEventTracker.removeEvent(event.getId().toString());
            event.setErrorMessage("Error submitting for processing: " + e.getMessage());
            kafkaTemplate.send(KafkaConstants.DLT_TOPIC_NAME, event.getId().toString(), event);

            // Ensure acknowledgment
            acknowledgment.acknowledge();
        }
    }

    /**
     * Consumes events from the retry topic.
     *
     * CRITICAL SCENARIO: Retry Topic Pattern
     * - Separate listener for retry topic allows specialized handling
     * - Can implement different processing logic or delays for retries
     * - Tracks retry attempts to prevent infinite retry loops
     * - Maintains processing state across retry attempts
     *
     * @param record The Kafka record containing the event
     * @param acknowledgment The acknowledgment callback
     */
    @KafkaListener(topics = KafkaConstants.RETRY_TOPIC_NAME, groupId = KafkaConstants.GROUP_ID)
    public void consumeRetryEvent(ConsumerRecord<String, Event> record, Acknowledgment acknowledgment) {
        Event event = record.value();
        StepStatus currentStepStatus = event.getCurrentStepStatus();

        log.info(
                "Received retry event: {} from topic: {}, partition: {}, offset: {}, step: {}, attempt: {} of step, {} total",
                event.getId(),
                record.topic(),
                record.partition(),
                record.offset(),
                event.getCurrentStep() != null ? event.getCurrentStep().name() : "unknown",
                currentStepStatus != null ? currentStepStatus.getAttempts() : "unknown",
                event.getProcessingAttempts());

        // CRITICAL SCENARIO: Retry Backoff
        // Implement backoff delay before processing retry
        // This gives the system time to recover from transient issues
        try {
            // Simple exponential backoff: 1s, 2s, 4s, etc.
            int attempts = currentStepStatus != null ? currentStepStatus.getAttempts() : 1;
            long backoffMs = (long) Math.pow(2, attempts - 1) * 1000;
            log.info("Applying backoff delay of {}ms before processing retry for event: {}", backoffMs, event.getId());
            Thread.sleep(backoffMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Backoff interrupted for event: {}", event.getId());
        }

        // Process the retry event the same way as a regular event
        consumeEvent(record, acknowledgment);
    }

    /**
     * Consumes events from the dead letter topic.
     *
     * CRITICAL SCENARIO: Dead Letter Topic Pattern
     * - Separate listener for DLT allows specialized handling
     * - Can implement alerting, monitoring, or manual intervention
     * - Provides visibility into failed messages
     * - Enables offline analysis of problematic messages
     *
     * @param record The Kafka record containing the event
     * @param acknowledgment The acknowledgment callback
     */
    @KafkaListener(topics = KafkaConstants.DLT_TOPIC_NAME, groupId = KafkaConstants.GROUP_ID)
    public void consumeDeadLetterEvent(ConsumerRecord<String, Event> record, Acknowledgment acknowledgment) {
        Event event = record.value();
        log.error(
                "Received dead letter event: {} from topic: {}, partition: {}, offset: {}, failed at step: {}, error: {}",
                event.getId(),
                record.topic(),
                record.partition(),
                record.offset(),
                event.getCurrentStep() != null ? event.getCurrentStep().name() : "unknown",
                event.getErrorMessage());

        // CRITICAL SCENARIO: Dead Letter Handling
        // Here you would implement logic to handle dead letter events
        // For example:
        // 1. Store in a database for manual review
        // 2. Send alerts to operations team
        // 3. Attempt specialized recovery procedures
        // 4. Log detailed diagnostics for analysis

        // For now, just acknowledge the message
        acknowledgment.acknowledge();
    }

    /**
     * Batch listener example for high-throughput processing.
     *
     * CRITICAL SCENARIO: Batch Processing
     * - Processes multiple records in a single call
     * - Reduces per-message overhead
     * - Can implement bulk database operations
     * - Significantly increases throughput for high-volume scenarios
     *
     * Note: This is commented out as it's an alternative to the individual record processing above.
     */
    /*
    @KafkaListener(topics = KafkaConstants.TOPIC_NAME, groupId = KafkaConstants.GROUP_ID + "-batch")
    public void consumeBatch(List<ConsumerRecord<String, Event>> records, Acknowledgment acknowledgment) {
        log.info("Received batch of {} records", records.size());

        // Process records in batch
        List<Event> events = records.stream()
                .map(ConsumerRecord::value)
                .collect(java.util.stream.Collectors.toList());

        try {
            // Process batch of events
            eventProcessingService.processBatch(events)
                .thenAccept(results -> {
                    log.info("Successfully processed batch of {} events", results.size());
                })
                .exceptionally(e -> {
                    log.error("Error processing batch", e);
                    return null;
                });

            // Acknowledge the entire batch
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error submitting batch for processing", e);
            acknowledgment.acknowledge();
        }
    }
    */

    /**
     * Implementation of ConsumerSeekAware for manual offset management.
     *
     * CRITICAL SCENARIO: Manual Offset Management
     * - Allows seeking to specific offsets
     * - Useful for reprocessing messages or skipping problematic messages
     * - Can implement custom recovery strategies
     */
    @Override
    public void onPartitionsAssigned(
            Map<TopicPartition, Long> assignments, ConsumerSeekAware.ConsumerSeekCallback callback) {
        // Example: Seek to specific offset for a partition
        // assignments.forEach((tp, offset) -> {
        //     if (tp.topic().equals(KafkaConstants.TOPIC_NAME) && tp.partition() == 0) {
        //         // Seek to beginning of partition 0
        //         callback.seekToBeginning(tp.topic(), tp.partition());
        //         // Or seek to specific offset
        //         // callback.seek(tp.topic(), tp.partition(), specificOffset);
        //     }
        // });

        // For now, just log the assignments
        log.info("Partitions assigned: {}", assignments);
    }
}
