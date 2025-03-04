package com.sbtl1.producer.service;

import com.example.common.constants.KafkaConstants;
import com.example.common.model.Event;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventProducerService {

    private static final Logger log = LogManager.getLogger(EventProducerService.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public CompletableFuture<SendResult<String, Object>> sendEvent(String payload) {
        Event event = Event.builder()
                .id(UUID.randomUUID())
                .payload(payload)
                .timestamp(LocalDateTime.now())
                .processingAttempts(0)
                .processed(false)
                .build();

        log.info("Sending event: {}", event);

        return kafkaTemplate
                .send(KafkaConstants.TOPIC_NAME, event.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Event sent successfully: {}", event.getId());
                        log.debug("Offset: {}", result.getRecordMetadata().offset());
                    } else {
                        log.error("Unable to send event: {}", event.getId(), ex);
                    }
                });
    }
}
