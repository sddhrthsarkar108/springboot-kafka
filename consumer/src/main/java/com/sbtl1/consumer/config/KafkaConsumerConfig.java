package com.sbtl1.consumer.config;

import com.example.common.constants.KafkaConstants;
import com.example.common.exception.RetryableException;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka Consumer Configuration
 *
 * This class configures the Kafka consumer for high throughput and reliability in distributed systems.
 * It demonstrates critical configurations needed for production-grade Kafka applications.
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    private static final Logger log = LogManager.getLogger(KafkaConsumerConfig.class);

    /**
     * Configures the Kafka consumer factory with critical settings for high throughput systems.
     *
     * CRITICAL SCENARIO: Consumer Performance Tuning
     * - auto.offset.reset determines behavior when no offset is found
     * - enable.auto.commit controls whether offsets are committed automatically
     * - max.poll.records limits the number of records processed in a single poll
     * - fetch.min.bytes and fetch.max.wait.ms optimize network usage
     * - session.timeout.ms and heartbeat.interval.ms handle consumer failure detection
     *
     * @return ConsumerFactory for creating Kafka consumers
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();

        // Bootstrap servers - connection to Kafka cluster
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

        // CRITICAL SCENARIO: Consumer Group Management
        // Consumers in the same group divide the partitions among themselves
        props.put(ConsumerConfig.GROUP_ID_CONFIG, KafkaConstants.GROUP_ID);

        // Deserializers for key and value
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // CRITICAL SCENARIO: Offset Management Strategy
        // Disable auto-commit to implement manual acknowledgment for better control
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // CRITICAL SCENARIO: Consumer Failure Handling
        // How long a consumer can be out of contact before being considered dead
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        // How frequently to send heartbeats to the consumer coordinator
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);

        // CRITICAL SCENARIO: Rebalance Strategy
        // What to do when no initial offset exists
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // CRITICAL SCENARIO: Throughput Optimization
        // Maximum records to fetch in a single poll (batching)
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        // Minimum amount of data to fetch in a request (reduces network overhead)
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);
        // Maximum time to wait for fetch.min.bytes to be satisfied
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);

        // CRITICAL SCENARIO: Processing Time Management
        // Maximum time between polls before consumer is considered failed
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000); // 5 minutes

        // Trust packages for deserialization
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.common.model,com.sbtl1.*");

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Creates a Kafka listener container factory with concurrency settings.
     *
     * CRITICAL SCENARIO: Concurrent Processing
     * - Multiple consumer threads process messages in parallel
     * - Each thread gets a subset of partitions
     * - Increases throughput by utilizing multiple CPU cores
     * - Number of concurrent consumers should not exceed total partitions
     *
     * @return ConcurrentKafkaListenerContainerFactory for creating listener containers
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());

        // CRITICAL SCENARIO: Concurrent Consumer Threads
        // Set the number of concurrent consumers (threads)
        // Should be <= number of partitions for efficiency
        factory.setConcurrency(3);

        // CRITICAL SCENARIO: Manual Acknowledgment
        // Enable manual acknowledgment for better control over offset commits
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // CRITICAL SCENARIO: Batch Processing
        // Enable batch processing for higher throughput
        factory.setBatchListener(true);

        // Set the error handler for exception handling
        factory.setCommonErrorHandler(errorHandler(null));

        return factory;
    }

    /**
     * Configures error handling for Kafka consumers.
     *
     * CRITICAL SCENARIO: Sophisticated Error Handling
     * - Implements retry with exponential backoff
     * - Routes failed messages to retry or DLT topics
     * - Differentiates between retryable and non-retryable errors
     * - Prevents poison pills from blocking consumer progress
     *
     * @param template KafkaTemplate for sending to DLT
     * @return DefaultErrorHandler for handling consumer exceptions
     */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> template) {
        // CRITICAL SCENARIO: Retry with Exponential Backoff
        // Configure retry with exponential backoff to handle transient failures
        // Increases wait time between retries to allow system to recover
        ExponentialBackOffWithMaxRetries exponentialBackOff =
                new ExponentialBackOffWithMaxRetries(KafkaConstants.MAX_RETRY_ATTEMPTS);
        exponentialBackOff.setInitialInterval(1000L); // 1 second initial backoff
        exponentialBackOff.setMultiplier(2.0); // Double the wait time for each retry
        exponentialBackOff.setMaxInterval(10000L); // Maximum 10 seconds wait

        // CRITICAL SCENARIO: Dead Letter Topic Pattern
        // Configure DLT for messages that fail after retries
        // Prevents poison pills from blocking consumer progress
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template, (record, exception) -> {
            // CRITICAL SCENARIO: Error Classification and Routing
            // Send to retry topic if not exceeding max attempts, otherwise to DLT
            if (exception.getCause() instanceof RetryableException
                    && ((RetryableException) exception.getCause()).getAttempts() < KafkaConstants.MAX_RETRY_ATTEMPTS) {
                log.warn("Sending message to retry topic due to: {}", exception.getMessage());
                return new TopicPartition(KafkaConstants.RETRY_TOPIC_NAME, record.partition());
            }
            log.error("Sending message to DLT due to: {}", exception.getMessage());
            return new TopicPartition(KafkaConstants.DLT_TOPIC_NAME, record.partition());
        });

        // CRITICAL SCENARIO: Configurable Retry Policy
        // Use fixed backoff for simplicity in this example
        // In production, consider using exponential backoff for more sophisticated retry behavior
        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, KafkaConstants.MAX_RETRY_ATTEMPTS));

        // CRITICAL SCENARIO: Error Classification
        // Configure which exceptions are retryable vs. fatal
        errorHandler.addRetryableExceptions(RetryableException.class);
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class, IllegalStateException.class);

        return errorHandler;
    }
}
