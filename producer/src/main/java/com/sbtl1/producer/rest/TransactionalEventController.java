package com.sbtl1.producer.rest;

import com.sbtl1.producer.service.TransactionalEventProducerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for demonstrating transactional Kafka producers.
 */
@RestController
@RequestMapping("/api/transactional-events")
@RequiredArgsConstructor
@Tag(name = "Transactional Event Producer API", description = "API for producing Kafka events with transactions")
public class TransactionalEventController {

    private static final Logger log = LogManager.getLogger(TransactionalEventController.class);
    private final TransactionalEventProducerService transactionalEventProducerService;

    /**
     * Sends a batch of events in a single transaction.
     *
     * @param payloads List of event payloads
     * @return Response indicating the events were submitted
     */
    @PostMapping("/batch")
    @Operation(
            summary = "Send a batch of events in a transaction",
            description = "Sends multiple events to Kafka in a single atomic transaction")
    public ResponseEntity<String> sendBatchInTransaction(@RequestBody List<String> payloads) {
        log.info("Received request to send {} events in a transaction", payloads.size());

        transactionalEventProducerService.sendEventsInTransaction(payloads);

        return ResponseEntity.accepted().body("Batch of " + payloads.size() + " events submitted in a transaction");
    }

    /**
     * Sends events to multiple topics in a single transaction.
     *
     * @param mainPayload Payload for the main topic
     * @param auditPayload Payload for the audit topic
     * @return Response indicating the events were submitted
     */
    @PostMapping("/cross-topic")
    @Operation(
            summary = "Send events to multiple topics in a transaction",
            description = "Demonstrates atomic writes across multiple Kafka topics")
    public ResponseEntity<String> sendCrossTopicTransaction(
            @RequestParam String mainPayload, @RequestParam String auditPayload) {
        log.info("Received request to send events across topics in a transaction");

        transactionalEventProducerService.sendCrossTopicTransaction(mainPayload, auditPayload);

        return ResponseEntity.accepted().body("Cross-topic events submitted in a transaction");
    }

    /**
     * Demonstrates coordinated database and Kafka transactions.
     *
     * @param payload Event payload
     * @param entityId ID of the database entity to modify
     * @return Response indicating the operation status
     */
    @PostMapping("/with-database")
    @Operation(
            summary = "Send event with database transaction",
            description = "Demonstrates coordination between database and Kafka transactions")
    public ResponseEntity<String> sendWithDatabaseTransaction(
            @RequestParam String payload, @RequestParam String entityId) {
        log.info("Received request to send event with database transaction for entity: {}", entityId);

        try {
            transactionalEventProducerService.sendWithDatabaseTransaction(payload, entityId);
            return ResponseEntity.accepted().body("Event submitted with coordinated database transaction");
        } catch (Exception e) {
            log.error("Transaction failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Transaction failed: " + e.getMessage());
        }
    }
}
