package com.example.common.constants;

/**
 * Constants for Kafka configuration.
 *
 * This class defines critical constants for Kafka configuration in a high-throughput distributed system.
 * These constants are shared between producer and consumer components.
 */
public class KafkaConstants {
    /**
     * CRITICAL SCENARIO: Topic Naming Strategy
     * - Clear, descriptive names help with system understanding and monitoring
     * - Prefix/suffix conventions can indicate message purpose and handling requirements
     * - Consistent naming facilitates automation and tooling
     */
    public static final String TOPIC_NAME = "long-running-events";

    /**
     * CRITICAL SCENARIO: Retry Topic Pattern
     * - Separate topic for retry attempts provides isolation from main processing flow
     * - Allows for specialized handling of retry attempts (e.g., delayed processing)
     * - Prevents retry messages from blocking main topic processing
     * - Enables monitoring and alerting specific to retry scenarios
     */
    public static final String RETRY_TOPIC_NAME = "long-running-events-retry";

    /**
     * CRITICAL SCENARIO: Dead Letter Topic Pattern
     * - Captures messages that fail processing after maximum retries
     * - Prevents poison pills from blocking processing indefinitely
     * - Enables offline analysis and manual intervention for failed messages
     * - Critical for maintaining system stability under failure conditions
     */
    public static final String DLT_TOPIC_NAME = "long-running-events-dlt";

    /**
     * CRITICAL SCENARIO: Consumer Group Strategy
     * - Defines which consumers work together to process partitions
     * - Multiple consumers in the same group divide the partitions among themselves
     * - Different groups receive their own copy of each message (pub/sub pattern)
     * - Naming should reflect the processing purpose for clarity
     */
    public static final String GROUP_ID = "event-processor-group";

    /**
     * CRITICAL SCENARIO: Retry Limits
     * - Prevents infinite retry loops that waste resources
     * - Balances between handling transient failures and failing fast for permanent issues
     * - Should be tuned based on observed failure patterns and recovery times
     * - Works with exponential backoff for efficient retry handling
     */
    public static final int MAX_RETRY_ATTEMPTS = 3;

    /**
     * CRITICAL SCENARIO: Partition Count
     * - Determines maximum parallelism for consumers in a group
     * - Should be >= the maximum number of concurrent consumers you plan to run
     * - Higher counts enable better scalability but increase broker overhead
     * - Should be planned based on throughput requirements and scaling needs
     */
    public static final int DEFAULT_PARTITION_COUNT = 3;

    /**
     * CRITICAL SCENARIO: Replication Factor
     * - Determines how many copies of data are maintained across brokers
     * - Higher values increase fault tolerance but use more storage
     * - Production systems typically use 3 for balance of reliability and resource usage
     * - Should be <= number of brokers in the cluster
     */
    public static final short DEFAULT_REPLICATION_FACTOR = 3;

    /**
     * CRITICAL SCENARIO: Consumer Concurrency
     * - Determines how many threads process messages within a single consumer instance
     * - Enables efficient CPU utilization on multi-core systems
     * - Should be tuned based on processing complexity and available resources
     * - Too high can cause thread contention, too low underutilizes resources
     */
    public static final int CONSUMER_CONCURRENCY = 3;

    /**
     * CRITICAL SCENARIO: Batch Size
     * - Controls how many records are processed in a single batch
     * - Larger batches improve throughput but increase memory usage and latency
     * - Critical for optimizing throughput in high-volume systems
     * - Should be tuned based on message size and processing characteristics
     */
    public static final int DEFAULT_BATCH_SIZE = 500;

    private KafkaConstants() {
        // Private constructor to prevent instantiation
    }
}
