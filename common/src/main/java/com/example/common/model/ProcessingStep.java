package com.example.common.model;

/**
 * Enum representing the different steps in event processing.
 * Each step has a name and an order value to determine the sequence.
 */
public enum ProcessingStep {
    VALIDATION(0, "Validate event data"),
    ENRICHMENT(1, "Enrich event with additional data"),
    TRANSFORMATION(2, "Transform event data"),
    PERSISTENCE(3, "Persist event to database"),
    NOTIFICATION(4, "Send notifications");

    private final int order;
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
}
