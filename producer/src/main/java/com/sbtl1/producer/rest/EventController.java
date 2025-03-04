package com.sbtl1.producer.rest;

import com.sbtl1.producer.service.EventProducerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Event Producer API", description = "API for producing Kafka events")
public class EventController {

    private static final Logger log = LogManager.getLogger(EventController.class);
    private final EventProducerService eventProducerService;

    @PostMapping
    @Operation(summary = "Send a new event", description = "Sends a new event to Kafka for processing")
    public ResponseEntity<String> sendEvent(@RequestBody String payload) {
        log.info("Received request to send event with payload: {}", payload);

        CompletableFuture<?> future = eventProducerService.sendEvent(payload);

        // Return immediately without waiting for Kafka confirmation
        return ResponseEntity.accepted().body("Event submitted for processing");
    }
}
