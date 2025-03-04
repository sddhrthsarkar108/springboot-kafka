package com.example.common.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event model for Kafka message processing.
 *
 * This class demonstrates critical patterns for modeling events in high-throughput Kafka systems:
 * 1. Unique identification for tracking and deduplication
 * 2. Timestamps for monitoring and SLA tracking
 * 3. State tracking for complex multi-step processing
 * 4. Error information for troubleshooting
 * 5. Processing history for auditing and debugging
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {
    /**
     * CRITICAL SCENARIO: Unique Message Identification
     * - UUID ensures globally unique identification across distributed systems
     * - Enables tracking of messages throughout the processing lifecycle
     * - Critical for deduplication and idempotent processing
     * - Facilitates correlation in logs and monitoring systems
     */
    private UUID id;

    /**
     * CRITICAL SCENARIO: Message Payload
     * - Contains the actual business data to be processed
     * - Should be structured for efficient serialization/deserialization
     * - Consider schema evolution for backward/forward compatibility
     * - May contain references to larger objects stored elsewhere for efficiency
     */
    private String payload;

    /**
     * CRITICAL SCENARIO: Temporal Tracking
     * - Captures when the event was created
     * - Enables monitoring of processing latency and SLA compliance
     * - Critical for time-based analytics and troubleshooting
     * - Helps identify processing bottlenecks
     */
    private LocalDateTime timestamp;

    /**
     * CRITICAL SCENARIO: Processing Attempt Tracking
     * - Counts total processing attempts across all steps
     * - Helps identify problematic messages that require excessive retries
     * - Critical for implementing global retry limits
     * - Provides insights into system stability and error rates
     */
    private int processingAttempts;

    /**
     * CRITICAL SCENARIO: Processing Status Flag
     * - Indicates whether processing has completed successfully
     * - Simplifies filtering of completed vs. in-progress events
     * - Critical for monitoring overall system progress
     * - Enables quick identification of stuck or failed messages
     */
    private boolean processed;

    /**
     * CRITICAL SCENARIO: Error Information
     * - Captures error details when processing fails
     * - Provides context for troubleshooting and analysis
     * - Critical for understanding failure patterns
     * - Helps determine whether errors are transient or permanent
     */
    private String errorMessage;

    /**
     * CRITICAL SCENARIO: Step-Based Processing
     * - Tracks the current processing step
     * - Enables resuming from the last successful step after failures
     * - Critical for implementing complex processing workflows
     * - Provides granular visibility into processing progress
     */
    private ProcessingStep currentStep;

    /**
     * CRITICAL SCENARIO: Processing History
     * - Maintains detailed status history for each processing step
     * - Enables comprehensive auditing and debugging
     * - Critical for understanding the complete processing lifecycle
     * - Facilitates root cause analysis for failures
     */
    @Builder.Default
    private List<StepStatus> stepStatuses = new ArrayList<>();

    /**
     * Initialize step statuses for all processing steps.
     *
     * CRITICAL SCENARIO: State Initialization
     * - Sets up the initial state for all processing steps
     * - Ensures consistent starting state for new events
     * - Critical for maintaining processing state integrity
     * - Prevents null pointer exceptions during processing
     */
    public void initializeStepStatuses() {
        stepStatuses = new ArrayList<>();
        for (ProcessingStep step : ProcessingStep.values()) {
            stepStatuses.add(StepStatus.builder()
                    .step(step)
                    .state(StepStatus.StepState.PENDING)
                    .attempts(0)
                    .build());
        }
        currentStep = ProcessingStep.getFirstStep();
    }

    /**
     * Get the status of the current processing step.
     *
     * CRITICAL SCENARIO: State Retrieval
     * - Provides quick access to the current step's status
     * - Simplifies status checks during processing
     * - Critical for making processing decisions based on current state
     * - Helps maintain processing flow integrity
     *
     * @return The current step's status
     */
    public StepStatus getCurrentStepStatus() {
        if (currentStep == null) {
            return null;
        }

        return stepStatuses.stream()
                .filter(status -> status.getStep() == currentStep)
                .findFirst()
                .orElse(null);
    }

    /**
     * Check if all steps have been completed successfully.
     *
     * CRITICAL SCENARIO: Completion Verification
     * - Verifies that all processing steps have completed successfully
     * - Ensures no steps were skipped or left incomplete
     * - Critical for maintaining data integrity
     * - Prevents premature completion of processing
     *
     * @return true if all steps are completed, false otherwise
     */
    public boolean areAllStepsCompleted() {
        if (stepStatuses == null || stepStatuses.isEmpty()) {
            return false;
        }

        return stepStatuses.stream().allMatch(status -> status.getState() == StepStatus.StepState.COMPLETED);
    }

    /**
     * Move to the next processing step.
     *
     * CRITICAL SCENARIO: Workflow Progression
     * - Advances the processing to the next step in the sequence
     * - Maintains the correct processing order
     * - Critical for implementing complex processing workflows
     * - Ensures all steps are processed in the defined sequence
     *
     * @return true if moved to next step, false if already at the last step
     */
    public boolean moveToNextStep() {
        if (currentStep == null) {
            currentStep = ProcessingStep.getFirstStep();
            return true;
        }

        ProcessingStep nextStep = currentStep.getNextStep();
        if (nextStep == null) {
            return false;
        }

        currentStep = nextStep;
        return true;
    }
}
