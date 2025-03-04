package com.example.common.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tracks the status of a single processing step for an event.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepStatus implements Serializable {
    private ProcessingStep step;
    private StepState state;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String errorMessage;
    private int attempts;

    /**
     * Enum representing the possible states of a processing step.
     */
    public enum StepState {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}
