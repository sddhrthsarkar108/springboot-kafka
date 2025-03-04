package com.example.common.exception;

import com.example.common.model.ProcessingStep;
import lombok.Getter;

/**
 * Exception for retryable processing failures in Kafka message handling.
 *
 * This class demonstrates critical patterns for exception handling in high-throughput Kafka systems:
 * 1. Clear distinction between retryable and non-retryable errors
 * 2. Tracking of retry attempts for backoff strategies
 * 3. Contextual information about the failure point
 * 4. Structured approach to error handling
 */
@Getter
public class RetryableException extends RuntimeException {
    /**
     * CRITICAL SCENARIO: Retry Attempt Tracking
     * - Counts how many times this operation has been attempted
     * - Critical for implementing exponential backoff
     * - Enables enforcement of maximum retry limits
     * - Prevents infinite retry loops that waste resources
     */
    private final int attempts;

    /**
     * CRITICAL SCENARIO: Failure Context
     * - Tracks which processing step failed
     * - Enables resuming from the failed step
     * - Critical for targeted error handling and recovery
     * - Provides valuable context for monitoring and alerting
     */
    private final ProcessingStep failedStep;

    /**
     * Constructor for generic retryable errors without step context.
     *
     * CRITICAL SCENARIO: Basic Retry Pattern
     * - Simplest form for transient errors that should be retried
     * - Tracks only the attempt count
     * - Useful for simple retry scenarios
     *
     * @param message Error message
     * @param attempts Number of attempts so far
     */
    public RetryableException(String message, int attempts) {
        super(message);
        this.attempts = attempts;
        this.failedStep = null;
    }

    /**
     * Constructor for generic retryable errors with cause but without step context.
     *
     * CRITICAL SCENARIO: Exception Chaining
     * - Preserves the original exception cause
     * - Maintains the full exception stack for debugging
     * - Critical for root cause analysis
     *
     * @param message Error message
     * @param cause Original exception that caused this error
     * @param attempts Number of attempts so far
     */
    public RetryableException(String message, Throwable cause, int attempts) {
        super(message, cause);
        this.attempts = attempts;
        this.failedStep = null;
    }

    /**
     * Constructor for step-specific retryable errors.
     *
     * CRITICAL SCENARIO: Step-Based Retry Pattern
     * - Includes information about which processing step failed
     * - Enables step-specific retry policies
     * - Critical for complex multi-step processing workflows
     * - Facilitates targeted monitoring and alerting
     *
     * @param message Error message
     * @param attempts Number of attempts so far
     * @param failedStep The processing step that failed
     */
    public RetryableException(String message, int attempts, ProcessingStep failedStep) {
        super(message);
        this.attempts = attempts;
        this.failedStep = failedStep;
    }

    /**
     * Constructor for step-specific retryable errors with cause.
     *
     * CRITICAL SCENARIO: Comprehensive Error Context
     * - Most detailed form with both cause and step information
     * - Provides complete context for sophisticated error handling
     * - Critical for complex systems with multiple failure modes
     * - Enables precise error classification and handling
     *
     * @param message Error message
     * @param cause Original exception that caused this error
     * @param attempts Number of attempts so far
     * @param failedStep The processing step that failed
     */
    public RetryableException(String message, Throwable cause, int attempts, ProcessingStep failedStep) {
        super(message, cause);
        this.attempts = attempts;
        this.failedStep = failedStep;
    }

    /**
     * Determine if this exception has exceeded the maximum retry attempts.
     *
     * CRITICAL SCENARIO: Retry Limit Enforcement
     * - Checks if the maximum retry limit has been reached
     * - Helps decide whether to retry or send to DLT
     * - Critical for preventing infinite retry loops
     * - Simplifies retry decision logic
     *
     * @param maxRetries Maximum number of retry attempts allowed
     * @return true if attempts have exceeded or reached the maximum, false otherwise
     */
    public boolean hasExceededRetryLimit(int maxRetries) {
        return attempts >= maxRetries;
    }

    /**
     * Create a new instance with incremented attempt count.
     *
     * CRITICAL SCENARIO: Retry State Management
     * - Creates a new exception with incremented attempt count
     * - Maintains the same error context across retry attempts
     * - Critical for tracking progression of retry attempts
     * - Preserves the original error information
     *
     * @return A new RetryableException with attempts incremented by 1
     */
    public RetryableException incrementAttempts() {
        return failedStep != null
                ? new RetryableException(getMessage(), getCause(), attempts + 1, failedStep)
                : new RetryableException(getMessage(), getCause(), attempts + 1);
    }
}
