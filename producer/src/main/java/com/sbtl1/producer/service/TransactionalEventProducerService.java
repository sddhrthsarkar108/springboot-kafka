package com.sbtl1.producer.service;

import com.example.common.constants.KafkaConstants;
import com.example.common.model.Event;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for producing events to Kafka topics using transactions.
 *
 * This service demonstrates the Transactional Producer pattern for Kafka:
 * 1. Atomic writes across multiple topics
 * 2. All-or-nothing semantics for message batches
 * 3. Exactly-once semantics in Kafka
 * 4. Integration with database transactions
 */
@Service
@RequiredArgsConstructor
public class TransactionalEventProducerService {

    private static final Logger log = LogManager.getLogger(TransactionalEventProducerService.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Sends a batch of events to Kafka in a single transaction.
     *
     * CRITICAL SCENARIO: Transactional Producer Pattern
     * - Ensures all messages are either committed or aborted atomically
     * - Critical for maintaining data consistency across topics
     * - Prevents partial message delivery in case of failures
     * - Enables exactly-once semantics when combined with EOS consumers
     *
     * @param payloads List of event payloads to send
     * @return CompletableFuture that completes when the transaction is committed
     */
    @Transactional
    public CompletableFuture<Void> sendEventsInTransaction(List<String> payloads) {
        log.info("Starting Kafka transaction for {} events", payloads.size());

        // Start a Kafka transaction
        kafkaTemplate.executeInTransaction(operations -> {
            for (String payload : payloads) {
                // Create a unique, idempotent event
                Event event = Event.builder()
                        .id(UUID.randomUUID())
                        .payload(payload)
                        .timestamp(LocalDateTime.now())
                        .processingAttempts(0)
                        .processed(false)
                        .build();

                log.info("Adding event to transaction: {}", event.getId());

                // Create a producer record with headers
                ProducerRecord<String, Object> record = new ProducerRecord<>(
                        KafkaConstants.TOPIC_NAME,
                        null,
                        System.currentTimeMillis(),
                        event.getId().toString(),
                        event);

                // Add headers for tracking and debugging
                record.headers().add(new RecordHeader("source", "transactional-producer".getBytes()));
                record.headers().add(new RecordHeader("event-type", "transactional".getBytes()));
                record.headers()
                        .add(new RecordHeader(
                                "trace-id", UUID.randomUUID().toString().getBytes()));

                // Send within the transaction
                operations.send(record);
            }
            return null;
        });

        log.info("Kafka transaction committed for {} events", payloads.size());
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Sends events to multiple topics in a single transaction.
     *
     * CRITICAL SCENARIO: Cross-Topic Transactional Writes
     * - Ensures atomic writes across multiple topics
     * - Critical for maintaining consistency in complex event flows
     * - Prevents partial updates across related topics
     *
     * @param mainTopicPayload Payload for the main topic
     * @param auditTopicPayload Payload for the audit topic
     * @return CompletableFuture that completes when the transaction is committed
     */
    @Transactional
    public CompletableFuture<Void> sendCrossTopicTransaction(String mainTopicPayload, String auditTopicPayload) {
        log.info("Starting cross-topic Kafka transaction");

        // Start a Kafka transaction
        kafkaTemplate.executeInTransaction(operations -> {
            // Create main event
            Event mainEvent = Event.builder()
                    .id(UUID.randomUUID())
                    .payload(mainTopicPayload)
                    .timestamp(LocalDateTime.now())
                    .processingAttempts(0)
                    .processed(false)
                    .build();

            // Create audit event (could be a different type in a real application)
            Event auditEvent = Event.builder()
                    .id(UUID.randomUUID())
                    .payload(auditTopicPayload)
                    .timestamp(LocalDateTime.now())
                    .processingAttempts(0)
                    .processed(false)
                    .build();

            log.info(
                    "Adding events to cross-topic transaction: main={}, audit={}",
                    mainEvent.getId(),
                    auditEvent.getId());

            // Send main event
            operations.send(KafkaConstants.TOPIC_NAME, mainEvent.getId().toString(), mainEvent);

            // Send audit event to a different topic
            // In a real application, this would be a dedicated audit topic
            operations.send(KafkaConstants.RETRY_TOPIC_NAME, auditEvent.getId().toString(), auditEvent);

            return null;
        });

        log.info("Cross-topic Kafka transaction committed");
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Demonstrates the integration of database transactions with Kafka transactions.
     *
     * CRITICAL SCENARIO: Database-Kafka Transaction Coordination
     * - Coordinates database and Kafka transactions for consistency
     * - Prevents dual-write problems between database and Kafka
     * - Critical for maintaining a consistent system of record
     * - In a real application, this would use JPA or another database API
     *
     * @param payload Event payload
     * @param entityId ID of the database entity being modified
     * @return CompletableFuture that completes when both transactions are committed
     */
    @Transactional
    public CompletableFuture<Void> sendWithDatabaseTransaction(String payload, String entityId) {
        log.info("Starting coordinated database and Kafka transaction for entity: {}", entityId);

        try {
            // Simulate database operation
            // In a real application, this would be a JPA repository call
            simulateDatabaseOperation(entityId);

            // Create event
            Event event = Event.builder()
                    .id(UUID.randomUUID())
                    .payload(payload)
                    .timestamp(LocalDateTime.now())
                    .processingAttempts(0)
                    .processed(false)
                    .build();

            log.info("Database operation successful, sending event: {}", event.getId());

            // Send event within the transaction
            kafkaTemplate.send(KafkaConstants.TOPIC_NAME, event.getId().toString(), event);

            log.info("Coordinated transaction committed successfully");
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            // Both database and Kafka transactions will be rolled back
            log.error("Error in coordinated transaction, rolling back: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Simulates a database operation.
     * In a real application, this would be a call to a repository or DAO.
     *
     * @param entityId ID of the entity to operate on
     */
    private void simulateDatabaseOperation(String entityId) {
        log.info("Simulating database operation for entity: {}", entityId);
        // In a real application, this would be a database call
        if (entityId.equals("error")) {
            throw new RuntimeException("Simulated database error");
        }
    }
}
