package com.example.common.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {
    private UUID id;
    private String payload;
    private LocalDateTime timestamp;
    private int processingAttempts;
    private boolean processed;
    private String errorMessage;

    // Step-based processing fields
    private ProcessingStep currentStep;

    @Builder.Default
    private List<StepStatus> stepStatuses = new ArrayList<>();

    /**
     * Initialize step statuses for all processing steps.
     * This should be called when creating a new event.
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
