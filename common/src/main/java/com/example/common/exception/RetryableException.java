package com.example.common.exception;

import com.example.common.model.ProcessingStep;
import lombok.Getter;

@Getter
public class RetryableException extends RuntimeException {
    private final int attempts;
    private final ProcessingStep failedStep;

    public RetryableException(String message, int attempts) {
        super(message);
        this.attempts = attempts;
        this.failedStep = null;
    }

    public RetryableException(String message, Throwable cause, int attempts) {
        super(message, cause);
        this.attempts = attempts;
        this.failedStep = null;
    }

    public RetryableException(String message, int attempts, ProcessingStep failedStep) {
        super(message);
        this.attempts = attempts;
        this.failedStep = failedStep;
    }

    public RetryableException(String message, Throwable cause, int attempts, ProcessingStep failedStep) {
        super(message, cause);
        this.attempts = attempts;
        this.failedStep = failedStep;
    }
}
