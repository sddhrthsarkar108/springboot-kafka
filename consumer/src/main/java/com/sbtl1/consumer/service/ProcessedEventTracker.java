package com.sbtl1.consumer.service;

import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for tracking processed events using Redis.
 *
 * This service demonstrates critical patterns for exactly-once processing in distributed Kafka consumers:
 * 1. Distributed state tracking with Redis
 * 2. Automatic cleanup with TTL (Time-To-Live)
 * 3. Thread-safe operations for concurrent processing
 * 4. Idempotent processing support
 */
@Service
public class ProcessedEventTracker {

    private static final Logger log = LogManager.getLogger(ProcessedEventTracker.class);
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * TTL for processed event records in Redis.
     * After this time, records will be automatically removed.
     * This should be set based on your message retention policy and redelivery expectations.
     */
    @Value("${kafka.processed-events.ttl-hours:24}")
    private int processedEventsTtlHours;

    /**
     * Prefix for Redis keys to avoid collisions with other applications.
     */
    private static final String KEY_PREFIX = "kafka:processed-event:";

    public ProcessedEventTracker(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Marks an event as being processed.
     *
     * CRITICAL SCENARIO: Distributed Deduplication
     * - Records the event ID in Redis with a TTL
     * - Returns false if the event was already being processed
     * - Critical for preventing duplicate processing in distributed systems
     * - Thread-safe for concurrent processing
     *
     * @param eventId The unique ID of the event
     * @return true if this is the first time seeing this event, false if it was already being processed
     */
    public boolean markEventAsProcessing(String eventId) {
        String key = KEY_PREFIX + eventId;

        // Use Redis SETNX (SET if Not eXists) for atomic check-and-set
        // This ensures thread-safety and prevents race conditions
        Boolean isNew =
                redisTemplate.opsForValue().setIfAbsent(key, "PROCESSING", Duration.ofHours(processedEventsTtlHours));

        if (Boolean.TRUE.equals(isNew)) {
            log.debug("Marked event {} as processing with TTL of {} hours", eventId, processedEventsTtlHours);
            return true;
        } else {
            log.debug("Event {} is already being processed", eventId);
            return false;
        }
    }

    /**
     * Marks an event as successfully processed.
     *
     * CRITICAL SCENARIO: Processing Status Tracking
     * - Updates the event status in Redis
     * - Maintains the same TTL for automatic cleanup
     * - Critical for tracking processing outcomes
     * - Enables verification of processing status
     *
     * @param eventId The unique ID of the event
     */
    public void markEventAsProcessed(String eventId) {
        String key = KEY_PREFIX + eventId;

        // Update the value to indicate successful processing
        // Maintain the TTL by setting it again
        redisTemplate.opsForValue().set(key, "PROCESSED", Duration.ofHours(processedEventsTtlHours));
        log.debug("Marked event {} as successfully processed", eventId);
    }

    /**
     * Checks if an event has been processed.
     *
     * CRITICAL SCENARIO: Processing Verification
     * - Verifies if an event has been processed before
     * - Critical for implementing exactly-once semantics
     * - Enables idempotent processing
     * - Useful for recovery scenarios
     *
     * @param eventId The unique ID of the event
     * @return true if the event has been processed, false otherwise
     */
    public boolean isEventProcessed(String eventId) {
        String key = KEY_PREFIX + eventId;
        String status = redisTemplate.opsForValue().get(key);

        boolean processed = "PROCESSED".equals(status);
        if (processed) {
            log.debug("Event {} has already been processed", eventId);
        }

        return processed;
    }

    /**
     * Checks if an event is currently being processed.
     *
     * CRITICAL SCENARIO: In-Progress Detection
     * - Detects if an event is currently being processed
     * - Critical for handling redelivered messages during processing
     * - Helps prevent concurrent processing of the same event
     * - Useful for monitoring and debugging
     *
     * @param eventId The unique ID of the event
     * @return true if the event is currently being processed, false otherwise
     */
    public boolean isEventBeingProcessed(String eventId) {
        String key = KEY_PREFIX + eventId;
        String status = redisTemplate.opsForValue().get(key);

        return "PROCESSING".equals(status);
    }

    /**
     * Removes the processing status for an event.
     *
     * CRITICAL SCENARIO: Processing Cancellation
     * - Removes the event from the processed events tracker
     * - Useful for handling failed processing that should be retried
     * - Critical for error recovery scenarios
     * - Enables manual intervention in processing flow
     *
     * @param eventId The unique ID of the event
     */
    public void removeEvent(String eventId) {
        String key = KEY_PREFIX + eventId;
        redisTemplate.delete(key);
        log.debug("Removed processing status for event {}", eventId);
    }
}
