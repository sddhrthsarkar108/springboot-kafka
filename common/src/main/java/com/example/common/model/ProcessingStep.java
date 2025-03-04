package com.example.common.model;

/**
 * Enum representing the different steps in event processing.
 *
 * This class demonstrates critical patterns for step-based processing in high-throughput Kafka systems:
 * 1. Ordered processing steps with clear progression
 * 2. Descriptive step names and documentation
 * 3. Step sequence management
 * 4. Modular processing pipeline design
 */
public enum ProcessingStep {
    /**
     * CRITICAL SCENARIO: Input Validation
     * - First step that validates event data integrity and format
     * - Prevents invalid data from entering the processing pipeline
     * - Fast-fails events that don't meet requirements
     * - Reduces wasted processing of invalid messages
     */
    VALIDATION(0, "Validate event data"),

    /**
     * CRITICAL SCENARIO: Data Enrichment
     * - Enhances events with additional data from external systems
     * - May involve API calls to reference data services
     * - Prepares data for subsequent processing steps
     * - Often involves I/O-bound operations that benefit from async processing
     */
    ENRICHMENT(1, "Enrich event with additional data"),

    /**
     * CRITICAL SCENARIO: Data Transformation
     * - Converts data into the required format for business processing
     * - Applies business rules and transformations
     * - Often CPU-intensive and benefits from dedicated thread pools
     * - Prepares data for persistence and downstream systems
     */
    TRANSFORMATION(2, "Transform event data"),

    /**
     * CRITICAL SCENARIO: Data Persistence
     * - Stores processed data in databases or other persistent storage
     * - Implements transactional boundaries for data integrity
     * - Often involves I/O-bound operations that benefit from connection pooling
     * - Critical for maintaining system of record and audit trail
     */
    PERSISTENCE(3, "Persist event to database"),

    /**
     * CRITICAL SCENARIO: Notification and Integration
     * - Sends notifications or triggers downstream processes
     * - Integrates with external systems via APIs, queues, or other mechanisms
     * - Often implements fire-and-forget pattern for non-critical notifications
     * - Completes the processing lifecycle
     */
    NOTIFICATION(4, "Send notifications");

    /**
     * CRITICAL SCENARIO: Step Ordering
     * - Numeric order value determines the sequence of processing steps
     * - Enables flexible reordering without changing code logic
     * - Critical for maintaining correct processing flow
     * - Facilitates insertion of new steps in the future
     */
    private final int order;

    /**
     * CRITICAL SCENARIO: Step Documentation
     * - Descriptive text explains the purpose of each step
     * - Improves observability and troubleshooting
     * - Critical for operational understanding
     * - Useful for generating documentation and monitoring dashboards
     */
    private final String description;

    ProcessingStep(int order, String description) {
        this.order = order;
        this.description = description;
    }

    public int getOrder() {
        return order;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get the next step in the processing sequence.
     *
     * CRITICAL SCENARIO: Step Progression Logic
     * - Determines the next step based on the current step's order
     * - Maintains the correct processing sequence
     * - Critical for implementing workflow progression
     * - Returns null when at the last step to indicate completion
     *
     * @return The next step or null if this is the last step.
     */
    public ProcessingStep getNextStep() {
        for (ProcessingStep step : ProcessingStep.values()) {
            if (step.getOrder() == this.order + 1) {
                return step;
            }
        }
        return null;
    }

    /**
     * Get the first step in the processing sequence.
     *
     * CRITICAL SCENARIO: Workflow Initialization
     * - Identifies the starting point for processing
     * - Ensures consistent workflow initialization
     * - Critical for maintaining processing integrity
     * - Provides a reliable entry point for new events
     *
     * @return The first processing step.
     */
    public static ProcessingStep getFirstStep() {
        for (ProcessingStep step : ProcessingStep.values()) {
            if (step.getOrder() == 0) {
                return step;
            }
        }
        return ProcessingStep.values()[0]; // Fallback to first defined step
    }

    /**
     * Get a step by its order value.
     *
     * CRITICAL SCENARIO: Dynamic Step Resolution
     * - Enables finding steps by their order value
     * - Useful for resuming processing from a specific point
     * - Critical for implementing recovery mechanisms
     * - Facilitates dynamic workflow management
     *
     * @param order The order value of the step to find
     * @return The step with the specified order, or null if not found
     */
    public static ProcessingStep getByOrder(int order) {
        for (ProcessingStep step : ProcessingStep.values()) {
            if (step.getOrder() == order) {
                return step;
            }
        }
        return null;
    }
}
