package com.sbtl1.consumer.service;

import com.example.common.constants.KafkaConstants;
import com.example.common.exception.RetryableException;
import com.example.common.model.Event;
import com.example.common.model.ProcessingStep;
import com.example.common.model.StepStatus;
import com.example.common.model.StepStatus.StepState;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

@Service
public class EventProcessingService {

    private static final Logger log = LogManager.getLogger(EventProcessingService.class);
    private final Random random = new Random();

    /**
     * Process an event with step-based processing and the ability to resume from failed steps.
     *
     * @param event The event to process
     * @return The processed event
     */
    public CompletableFuture<Event> processEvent(Event event) {
        log.info(
                "Starting to process event: {}, current step: {}",
                event.getId(),
                event.getCurrentStep() != null ? event.getCurrentStep().name() : "NULL");

        // Initialize step statuses if not already initialized
        if (event.getStepStatuses() == null || event.getStepStatuses().isEmpty()) {
            event.initializeStepStatuses();
            log.info("Initialized step statuses for event: {}", event.getId());
        }

        // If no current step is set, start with the first step
        if (event.getCurrentStep() == null) {
            event.setCurrentStep(ProcessingStep.getFirstStep());
            log.info("Set initial step to {} for event: {}", event.getCurrentStep(), event.getId());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Process the current step
                return processCurrentStep(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Event processing interrupted: {}", event.getId(), e);

                // Update the current step status
                StepStatus currentStepStatus = event.getCurrentStepStatus();
                if (currentStepStatus != null) {
                    currentStepStatus.setState(StepState.FAILED);
                    currentStepStatus.setEndTime(LocalDateTime.now());
                    currentStepStatus.setErrorMessage("Processing interrupted: " + e.getMessage());
                }

                event.setErrorMessage("Processing interrupted: " + e.getMessage());
                return event;
            }
        });
    }

    /**
     * Process the current step of the event.
     *
     * @param event The event to process
     * @return The processed event
     * @throws InterruptedException If the processing is interrupted
     */
    private Event processCurrentStep(Event event) throws InterruptedException {
        ProcessingStep currentStep = event.getCurrentStep();
        StepStatus currentStepStatus = event.getCurrentStepStatus();

        if (currentStepStatus == null) {
            log.error("No status found for current step {} of event {}", currentStep, event.getId());
            event.setErrorMessage("No status found for current step: " + currentStep);
            return event;
        }

        // Increment processing attempts for this step
        currentStepStatus.setAttempts(currentStepStatus.getAttempts() + 1);
        event.setProcessingAttempts(event.getProcessingAttempts() + 1);

        // Update step status to IN_PROGRESS
        currentStepStatus.setState(StepState.IN_PROGRESS);
        currentStepStatus.setStartTime(LocalDateTime.now());

        log.info(
                "Processing step {} for event {} (attempt {} of step, {} total)",
                currentStep,
                event.getId(),
                currentStepStatus.getAttempts(),
                event.getProcessingAttempts());

        // Simulate step processing time (between 1-3 seconds per step)
        int processingTime = 1000 + random.nextInt(2000);
        log.info("Step {} processing for event {} will take {} ms", currentStep, event.getId(), processingTime);
        TimeUnit.MILLISECONDS.sleep(processingTime);

        // Simulate random failures (20% chance of failure)
        if (random.nextInt(10) < 2) {
            // If we've already tried MAX_RETRY_ATTEMPTS times for this step, mark as permanent failure
            if (currentStepStatus.getAttempts() >= KafkaConstants.MAX_RETRY_ATTEMPTS) {
                log.error(
                        "Step {} processing permanently failed for event {} after {} attempts",
                        currentStep,
                        event.getId(),
                        currentStepStatus.getAttempts());

                currentStepStatus.setState(StepState.FAILED);
                currentStepStatus.setEndTime(LocalDateTime.now());
                currentStepStatus.setErrorMessage("Step processing failed after maximum retry attempts");

                event.setErrorMessage("Step " + currentStep + " failed after maximum retry attempts");
                return event;
            }

            // Otherwise, throw a retryable exception with step information
            String errorMsg = "Simulated processing failure for step " + currentStep + " of event: " + event.getId();
            log.warn(
                    errorMsg + " (attempt {} of {} for this step)",
                    currentStepStatus.getAttempts(),
                    KafkaConstants.MAX_RETRY_ATTEMPTS);

            currentStepStatus.setState(StepState.FAILED);
            currentStepStatus.setEndTime(LocalDateTime.now());
            currentStepStatus.setErrorMessage(errorMsg);

            throw new RetryableException(errorMsg, currentStepStatus.getAttempts(), currentStep);
        }

        // Success case for this step
        log.info("Successfully processed step {} for event {}", currentStep, event.getId());
        currentStepStatus.setState(StepState.COMPLETED);
        currentStepStatus.setEndTime(LocalDateTime.now());

        // Move to the next step if there is one
        boolean hasNextStep = event.moveToNextStep();
        if (hasNextStep) {
            log.info("Moving to next step {} for event {}", event.getCurrentStep(), event.getId());
            return processCurrentStep(event); // Process the next step recursively
        } else {
            // All steps completed
            log.info("All steps completed for event {}", event.getId());
            event.setProcessed(true);
            return event;
        }
    }
}
