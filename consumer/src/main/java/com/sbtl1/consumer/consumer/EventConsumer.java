package com.sbtl1.consumer.consumer;

import com.example.common.constants.KafkaConstants;
import com.example.common.exception.RetryableException;
import com.example.common.model.Event;
import com.example.common.model.ProcessingStep;
import com.example.common.model.StepStatus;
import com.sbtl1.consumer.service.EventProcessingService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventConsumer {

    private static final Logger log = LogManager.getLogger(EventConsumer.class);
    private final EventProcessingService eventProcessingService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = KafkaConstants.TOPIC_NAME, groupId = KafkaConstants.GROUP_ID)
    public void consumeEvent(ConsumerRecord<String, Event> record, Acknowledgment acknowledgment) {
        Event event = record.value();
        log.info("Received event: {} from topic: {}", event.getId(), record.topic());

        try {
            // Acknowledge the message immediately to prevent redelivery
            // This implements an "at-most-once" delivery pattern
            acknowledgment.acknowledge();

            // Process the event asynchronously without waiting for completion
            eventProcessingService
                    .processEvent(event)
                    .thenAccept(processedEvent -> {
                        if (processedEvent.isProcessed()) {
                            log.info("Event processed successfully: {}", processedEvent.getId());
                        } else {
                            // If processing failed but it's not retryable, send to DLT
                            log.error("Event processing failed: {}", processedEvent.getId());
                            kafkaTemplate.send(
                                    KafkaConstants.DLT_TOPIC_NAME,
                                    processedEvent.getId().toString(),
                                    processedEvent);
                        }
                    })
                    .exceptionally(e -> {
                        Throwable cause = e.getCause();
                        if (cause instanceof RetryableException) {
                            RetryableException re = (RetryableException) cause;
                            ProcessingStep failedStep = re.getFailedStep();

                            log.warn(
                                    "Retryable error occurred for event: {} at step: {} (attempt {})",
                                    event.getId(),
                                    failedStep != null ? failedStep.name() : "unknown",
                                    re.getAttempts());

                            // If we haven't exceeded max retries, send to retry topic
                            if (re.getAttempts() < KafkaConstants.MAX_RETRY_ATTEMPTS) {
                                // The current step is already set in the event
                                kafkaTemplate.send(
                                        KafkaConstants.RETRY_TOPIC_NAME,
                                        event.getId().toString(),
                                        event);
                            } else {
                                // Otherwise, send to DLT
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
                            // Non-retryable error, send to DLT
                            log.error("Non-retryable error occurred for event: {}", event.getId(), e);
                            event.setErrorMessage("Non-retryable error: " + e.getMessage());
                            kafkaTemplate.send(
                                    KafkaConstants.DLT_TOPIC_NAME, event.getId().toString(), event);
                        }
                        return null;
                    });
        } catch (Exception e) {
            log.error("Error submitting event for processing: {}", event.getId(), e);
            event.setErrorMessage("Error submitting for processing: " + e.getMessage());
            kafkaTemplate.send(KafkaConstants.DLT_TOPIC_NAME, event.getId().toString(), event);
            acknowledgment.acknowledge();
        }
    }

    @KafkaListener(topics = KafkaConstants.RETRY_TOPIC_NAME, groupId = KafkaConstants.GROUP_ID)
    public void consumeRetryEvent(ConsumerRecord<String, Event> record, Acknowledgment acknowledgment) {
        Event event = record.value();
        StepStatus currentStepStatus = event.getCurrentStepStatus();

        log.info(
                "Received retry event: {} from topic: {}, step: {}, attempt: {} of step, {} total",
                event.getId(),
                record.topic(),
                event.getCurrentStep() != null ? event.getCurrentStep().name() : "unknown",
                currentStepStatus != null ? currentStepStatus.getAttempts() : "unknown",
                event.getProcessingAttempts());

        // Process the retry event the same way as a regular event
        consumeEvent(record, acknowledgment);
    }

    @KafkaListener(topics = KafkaConstants.DLT_TOPIC_NAME, groupId = KafkaConstants.GROUP_ID)
    public void consumeDeadLetterEvent(ConsumerRecord<String, Event> record, Acknowledgment acknowledgment) {
        Event event = record.value();
        log.error(
                "Received dead letter event: {} from topic: {}, failed at step: {}, error: {}",
                event.getId(),
                record.topic(),
                event.getCurrentStep() != null ? event.getCurrentStep().name() : "unknown",
                event.getErrorMessage());

        // Here you would implement logic to handle dead letter events
        // For example, storing them in a database for manual review

        acknowledgment.acknowledge();
    }
}
