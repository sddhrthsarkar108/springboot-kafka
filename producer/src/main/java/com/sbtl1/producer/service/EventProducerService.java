package com.sbtl1.producer.service;

import com.example.common.constants.KafkaConstants;
import com.example.common.model.Event;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

/**
 * Service responsible for producing events to Kafka topics.
 *
 * This service demonstrates several critical patterns for high-throughput Kafka producers:
 * 1. Asynchronous message production with CompletableFuture
 * 2. Proper message key selection for partition affinity
 * 3. Error handling and logging
 * 4. Message headers for metadata
 * 5. Idempotent event generation
 */
@Service
@RequiredArgsConstructor
public class EventProducerService {

    private static final Logger log = LogManager.getLogger(EventProducerService.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Sends an event to Kafka asynchronously.
     *
     * CRITICAL SCENARIO: Asynchronous Message Production
     * - Returns CompletableFuture to allow non-blocking operation
     * - Critical for high throughput systems to avoid blocking threads
     * - Allows the caller to decide whether to wait for confirmation or continue immediately
     * - Provides callback mechanism for success/failure handling
     *
     * CRITICAL SCENARIO: Message Key Selection Strategy
     * - Using event ID as the key ensures related events go to the same partition
     * - Enables ordering guarantees for events with the same key
     * - Facilitates efficient consumer-side processing of related events
     * - Helps with even distribution of messages across partitions
     *
     * @param payload The event payload to send
     * @return CompletableFuture with the result of the send operation
     */
    public CompletableFuture<SendResult<String, Object>> sendEvent(String payload) {
        // Create a unique, idempotent event
        Event event = Event.builder()
                .id(UUID.randomUUID())
                .payload(payload)
                .timestamp(LocalDateTime.now())
                .processingAttempts(0)
                .processed(false)
                .build();

        log.info("Sending event: {}", event);

        // CRITICAL SCENARIO: Message Headers for Metadata
        // Create a producer record with headers for additional metadata
        ProducerRecord<String, Object> record = new ProducerRecord<>(
                KafkaConstants.TOPIC_NAME,
                null, // Let Kafka assign the partition based on the key
                System.currentTimeMillis(), // Timestamp
                event.getId().toString(), // Key - ensures related events go to same partition
                event // Value
                );

        // Add headers for tracking and debugging
        record.headers().add(new RecordHeader("source", "event-producer".getBytes()));
        record.headers().add(new RecordHeader("event-type", "standard".getBytes()));
        record.headers()
                .add(new RecordHeader("trace-id", UUID.randomUUID().toString().getBytes()));

        // CRITICAL SCENARIO: Asynchronous Send with Completion Callback
        // Send asynchronously and handle the result
        return kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex == null) {
                // CRITICAL SCENARIO: Logging for Observability
                // Log success with metadata for tracing and monitoring
                log.info(
                        "Event sent successfully: {}, topic: {}, partition: {}, offset: {}",
                        event.getId(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                // CRITICAL SCENARIO: Error Handling for Failed Sends
                // Comprehensive error logging for troubleshooting
                log.error("Unable to send event: {}, error: {}", event.getId(), ex.getMessage(), ex);

                // In a production system, you might:
                // 1. Store failed messages in a local buffer for retry
                // 2. Publish to a monitoring system
                // 3. Trigger alerts if failure rate exceeds threshold
            }
        });
    }

    /**
     * Sends an event with a specified key for ensuring message ordering.
     *
     * CRITICAL SCENARIO: Ordered Message Delivery
     * - Messages with the same key go to the same partition
     * - Ensures strict ordering for events that must be processed sequentially
     * - Essential for event sourcing and state-dependent processing
     *
     * @param key The key to use for the message (determines the partition)
     * @param payload The event payload
     * @return CompletableFuture with the result of the send operation
     */
    public CompletableFuture<SendResult<String, Object>> sendOrderedEvent(String key, String payload) {
        Event event = Event.builder()
                .id(UUID.randomUUID())
                .payload(payload)
                .timestamp(LocalDateTime.now())
                .processingAttempts(0)
                .processed(false)
                .build();

        log.info("Sending ordered event with key {}: {}", key, event);

        return kafkaTemplate.send(KafkaConstants.TOPIC_NAME, key, event).whenComplete((result, ex) -> {
            if (ex == null) {
                log.info(
                        "Ordered event sent successfully: {}, key: {}, partition: {}",
                        event.getId(),
                        key,
                        result.getRecordMetadata().partition());
            } else {
                log.error("Unable to send ordered event: {}, key: {}", event.getId(), key, ex);
            }
        });
    }

    /**
     * Sends a batch of events with controlled rate limiting.
     *
     * CRITICAL SCENARIO: Rate Limiting and Backpressure
     * - Controls the rate of message production to prevent overwhelming consumers
     * - Implements backpressure to maintain system stability under high load
     * - Uses CompletableFuture.allOf to track completion of all messages
     *
     * @param payloads List of event payloads to send
     * @return CompletableFuture that completes when all events are sent
     */
    public CompletableFuture<Void> sendBatchWithRateLimit(Iterable<String> payloads) {
        // Create a list to hold all the futures
        java.util.List<CompletableFuture<SendResult<String, Object>>> futures = new java.util.ArrayList<>();

        // CRITICAL SCENARIO: Rate Limiting Implementation
        // Send messages with a controlled rate to prevent overwhelming the broker
        int messageCount = 0;
        for (String payload : payloads) {
            // Add a small delay every 100 messages to prevent overwhelming the broker
            if (messageCount > 0 && messageCount % 100 == 0) {
                try {
                    // Implement backpressure with a small delay
                    TimeUnit.MILLISECONDS.sleep(50);
                    log.info("Applied rate limiting after {} messages", messageCount);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Rate limiting interrupted", e);
                }
            }

            // Send the message and collect the future
            futures.add(sendEvent(payload));
            messageCount++;
        }

        // Store the final message count for use in the lambda
        final int finalMessageCount = messageCount;

        // CRITICAL SCENARIO: Batch Completion Tracking
        // Wait for all messages to be sent and handle any failures
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Successfully sent batch of {} messages", finalMessageCount);
                    } else {
                        log.error("Error sending batch of messages", ex);
                    }
                });
    }
}
