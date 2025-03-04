package com.sbtl1.consumer.service;

import com.example.common.exception.RetryableException;
import com.example.common.model.Event;
import com.example.common.model.ProcessingStep;
import com.example.common.model.StepStatus;
import com.example.common.model.StepStatus.StepState;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

/**
 * Service responsible for processing events consumed from Kafka.
 *
 * This service demonstrates several critical patterns for high-throughput event processing:
 * 1. Step-based processing with state tracking
 * 2. Asynchronous processing with CompletableFuture
 * 3. Retry handling with step resumption
 * 4. Failure isolation and circuit breaking
 * 5. Resource management with dedicated thread pools
 */
@Service
public class EventProcessingService {

    private static final Logger log = LogManager.getLogger(EventProcessingService.class);
    private final Random random = new Random();

    // CRITICAL SCENARIO: Dedicated Thread Pool
    // Use a dedicated thread pool for CPU-bound processing tasks
    // Prevents thread starvation in the main application thread pool
    // Size should be tuned based on available CPU cores and workload characteristics
    private final Executor processingExecutor =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    // CRITICAL SCENARIO: I/O Thread Pool
    // Separate thread pool for I/O-bound operations (database, external services)
    // Allows for higher concurrency since I/O operations spend most time waiting
    private final Executor ioExecutor = Executors.newFixedThreadPool(20);

    /**
     * Process an event with step-based processing and the ability to resume from failed steps.
     *
     * CRITICAL SCENARIO: Stateful Processing with Resumability
     * - Tracks processing state across multiple steps
     * - Enables resuming from the last successful step after failures
     * - Maintains processing history for auditing and debugging
     * - Prevents duplicate processing of already completed steps
     *
     * @param event The event to process
     * @return The processed event
     */
    public CompletableFuture<Event> processEvent(Event event) {
        log.info(
                "Starting to process event: {}, current step: {}",
                event.getId(),
                event.getCurrentStep() != null ? event.getCurrentStep().name() : "NULL");

        // CRITICAL SCENARIO: State Initialization
        // Initialize step statuses if not already initialized
        if (event.getStepStatuses() == null || event.getStepStatuses().isEmpty()) {
            event.initializeStepStatuses();
            log.info("Initialized step statuses for event: {}", event.getId());
        }

        // CRITICAL SCENARIO: Process Resumption
        // If no current step is set, start with the first step
        // If a step is already set, processing will resume from that step
        if (event.getCurrentStep() == null) {
            event.setCurrentStep(ProcessingStep.getFirstStep());
            log.info("Set initial step to {} for event: {}", event.getCurrentStep(), event.getId());
        }

        // CRITICAL SCENARIO: Asynchronous Processing with Dedicated Thread Pool
        // Use a dedicated thread pool for CPU-intensive processing
        // Prevents blocking the main application thread pool
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        // Process the current step
                        return processCurrentStep(event);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("Event processing interrupted: {}", event.getId(), e);

                        // CRITICAL SCENARIO: Graceful Interruption Handling
                        // Update the current step status to reflect the interruption
                        StepStatus currentStepStatus = event.getCurrentStepStatus();
                        if (currentStepStatus != null) {
                            currentStepStatus.setState(StepState.FAILED);
                            currentStepStatus.setEndTime(LocalDateTime.now());
                            currentStepStatus.setErrorMessage("Processing interrupted: " + e.getMessage());
                        }

                        event.setErrorMessage("Processing interrupted: " + e.getMessage());
                        return event;
                    }
                },
                processingExecutor);
    }

    /**
     * Process the current step of the event.
     *
     * CRITICAL SCENARIO: Step-Based Processing
     * - Breaks complex processing into discrete, manageable steps
     * - Enables fine-grained tracking of progress
     * - Allows for step-specific retry and error handling
     * - Facilitates resuming from the last successful step
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

        // CRITICAL SCENARIO: Attempt Tracking
        // Increment processing attempts for this step
        // Critical for implementing retry limits and backoff strategies
        currentStepStatus.setAttempts(currentStepStatus.getAttempts() + 1);
        event.setProcessingAttempts(event.getProcessingAttempts() + 1);

        // CRITICAL SCENARIO: State Transition Management
        // Update step status to IN_PROGRESS
        // Maintains accurate state for monitoring and recovery
        currentStepStatus.setState(StepState.IN_PROGRESS);
        currentStepStatus.setStartTime(LocalDateTime.now());

        log.info(
                "Processing step {} for event {} (attempt {} of step, {} total)",
                currentStep,
                event.getId(),
                currentStepStatus.getAttempts(),
                event.getProcessingAttempts());

        // CRITICAL SCENARIO: Step-Specific Processing Logic
        // Each step would have its own specialized processing logic
        // Here we're simulating with random processing times and failures
        switch (currentStep) {
            case VALIDATION:
                return processValidationStep(event, currentStepStatus);
            case ENRICHMENT:
                return processEnrichmentStep(event, currentStepStatus);
            case TRANSFORMATION:
                return processTransformationStep(event, currentStepStatus);
            case PERSISTENCE:
                return processPersistenceStep(event, currentStepStatus);
            case NOTIFICATION:
                return processNotificationStep(event, currentStepStatus);
            default:
                // Should never happen with enum
                log.error("Unknown step: {} for event {}", currentStep, event.getId());
                currentStepStatus.setState(StepState.FAILED);
                currentStepStatus.setEndTime(LocalDateTime.now());
                currentStepStatus.setErrorMessage("Unknown processing step");
                event.setErrorMessage("Unknown processing step: " + currentStep);
                return event;
        }
    }

    /**
     * Process the validation step.
     *
     * CRITICAL SCENARIO: Input Validation
     * - Validates event data before further processing
     * - Prevents invalid data from propagating through the system
     * - Fast-fails events that don't meet requirements
     */
    private Event processValidationStep(Event event, StepStatus stepStatus) throws InterruptedException {
        log.info("Validating event: {}", event.getId());

        // Simulate validation processing time (quick)
        TimeUnit.MILLISECONDS.sleep(100 + random.nextInt(200));

        // Simulate validation logic - check if payload is present
        if (event.getPayload() == null || event.getPayload().trim().isEmpty()) {
            stepStatus.setState(StepState.FAILED);
            stepStatus.setEndTime(LocalDateTime.now());
            stepStatus.setErrorMessage("Event payload cannot be empty");
            event.setErrorMessage("Validation failed: Empty payload");
            return event;
        }

        // Success case
        return completeStepAndContinue(event, stepStatus);
    }

    /**
     * Process the enrichment step.
     *
     * CRITICAL SCENARIO: Data Enrichment
     * - Enriches events with additional data from external systems
     * - Handles external service failures with circuit breaking
     * - Implements timeouts to prevent blocking on slow services
     */
    private Event processEnrichmentStep(Event event, StepStatus stepStatus) throws InterruptedException {
        log.info("Enriching event: {}", event.getId());

        // CRITICAL SCENARIO: External Service Integration
        // In a real system, this would call external services to enrich the event
        // Here we simulate with a longer processing time and potential failures

        // Simulate enrichment processing time (medium)
        TimeUnit.MILLISECONDS.sleep(300 + random.nextInt(500));

        // CRITICAL SCENARIO: Circuit Breaking
        // Simulate occasional external service failures (30% chance)
        if (random.nextInt(10) < 3) {
            // CRITICAL SCENARIO: Retryable External Service Failure
            // This type of failure is temporary and can be retried
            String errorMsg = "External service unavailable for enrichment";
            log.warn(errorMsg + " for event: {}", event.getId());

            stepStatus.setState(StepState.FAILED);
            stepStatus.setEndTime(LocalDateTime.now());
            stepStatus.setErrorMessage(errorMsg);

            throw new RetryableException(errorMsg, stepStatus.getAttempts(), stepStatus.getStep());
        }

        // Success case - add enriched data to payload
        String enrichedPayload = event.getPayload() + " [ENRICHED]";
        event.setPayload(enrichedPayload);

        return completeStepAndContinue(event, stepStatus);
    }

    /**
     * Process the transformation step.
     *
     * CRITICAL SCENARIO: Data Transformation
     * - Transforms event data into the required format
     * - CPU-intensive operation that benefits from dedicated thread pool
     * - Handles complex business logic
     */
    private Event processTransformationStep(Event event, StepStatus stepStatus) throws InterruptedException {
        log.info("Transforming event: {}", event.getId());

        // Simulate transformation processing time (varies based on payload size)
        int processingTime = 200 + (event.getPayload().length() * 5);
        TimeUnit.MILLISECONDS.sleep(Math.min(processingTime, 1000)); // Cap at 1 second

        // Simulate transformation logic
        String transformedPayload = event.getPayload().toUpperCase() + " [TRANSFORMED]";
        event.setPayload(transformedPayload);

        return completeStepAndContinue(event, stepStatus);
    }

    /**
     * Process the persistence step.
     *
     * CRITICAL SCENARIO: Reliable Persistence
     * - Persists event data to a database or other storage
     * - Handles database connection issues and constraints
     * - Implements transactional boundaries
     */
    private Event processPersistenceStep(Event event, StepStatus stepStatus) throws InterruptedException {
        log.info("Persisting event: {}", event.getId());

        // CRITICAL SCENARIO: Database Operation
        // In a real system, this would persist to a database
        // Here we simulate with a longer processing time and potential failures

        // Simulate persistence processing time (longer)
        TimeUnit.MILLISECONDS.sleep(500 + random.nextInt(1000));

        // CRITICAL SCENARIO: Database Failure Handling
        // Simulate occasional database failures (20% chance)
        if (random.nextInt(10) < 2) {
            // CRITICAL SCENARIO: Transient Database Failure
            String errorMsg = "Database connection error during persistence";
            log.warn(errorMsg + " for event: {}", event.getId());

            stepStatus.setState(StepState.FAILED);
            stepStatus.setEndTime(LocalDateTime.now());
            stepStatus.setErrorMessage(errorMsg);

            throw new RetryableException(errorMsg, stepStatus.getAttempts(), stepStatus.getStep());
        }

        // Success case
        return completeStepAndContinue(event, stepStatus);
    }

    /**
     * Process the notification step.
     *
     * CRITICAL SCENARIO: Notification Delivery
     * - Sends notifications about processed events
     * - Handles notification service failures
     * - Implements fire-and-forget pattern for non-critical notifications
     */
    private Event processNotificationStep(Event event, StepStatus stepStatus) throws InterruptedException {
        log.info("Sending notifications for event: {}", event.getId());

        // Simulate notification processing time (quick)
        TimeUnit.MILLISECONDS.sleep(100 + random.nextInt(300));

        // CRITICAL SCENARIO: Non-Critical Failure Handling
        // For notifications, we might choose to continue even if they fail
        // since this is the last step and notifications might be non-critical
        if (random.nextInt(10) < 1) {
            log.warn("Notification delivery failed for event: {}, but continuing", event.getId());
            // We still mark the step as completed since this is non-critical
        }

        // Success case - this is the final step
        stepStatus.setState(StepState.COMPLETED);
        stepStatus.setEndTime(LocalDateTime.now());

        // Mark the entire event as processed since this is the last step
        event.setProcessed(true);
        log.info("All processing completed for event: {}", event.getId());

        return event;
    }

    /**
     * Helper method to complete the current step and move to the next one.
     */
    private Event completeStepAndContinue(Event event, StepStatus currentStepStatus) throws InterruptedException {
        // Success case for this step
        log.info("Successfully processed step {} for event {}", currentStepStatus.getStep(), event.getId());
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

    /**
     * Process a batch of events in parallel.
     *
     * CRITICAL SCENARIO: Parallel Batch Processing
     * - Processes multiple events concurrently for higher throughput
     * - Uses CompletableFuture.allOf for efficient parallel processing
     * - Manages thread pool resources effectively
     * - Collects and reports results for the entire batch
     *
     * @param events List of events to process
     * @return CompletableFuture with the list of processed events
     */
    public CompletableFuture<List<Event>> processBatch(List<Event> events) {
        log.info("Processing batch of {} events", events.size());

        // Create a list of futures, one for each event
        List<CompletableFuture<Event>> futures =
                events.stream().map(this::processEvent).collect(Collectors.toList());

        // CRITICAL SCENARIO: Parallel Processing with Result Collection
        // Wait for all events to be processed and collect the results
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).collect(Collectors.toList()));
    }
}
