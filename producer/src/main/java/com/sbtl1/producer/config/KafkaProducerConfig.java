package com.sbtl1.producer.config;

import com.example.common.constants.KafkaConstants;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Kafka Producer Configuration
 *
 * This class configures the Kafka producer for high throughput and reliability in distributed systems.
 * It demonstrates critical configurations needed for production-grade Kafka applications.
 */
@Configuration
public class KafkaProducerConfig {

    /**
     * Creates the main topic with multiple partitions for parallel processing.
     *
     * CRITICAL SCENARIO: Partition Strategy
     * - Using multiple partitions (3) allows for parallel processing by multiple consumers
     * - Each partition can be consumed by a single consumer within a consumer group
     * - Enables horizontal scaling of consumers for high throughput
     * - Partition count should be planned based on expected throughput and consumer scaling needs
     *
     * CRITICAL SCENARIO: Replication Factor
     * - In production, set replicas > 1 for fault tolerance (here using 1 for local dev)
     * - Higher replication factor (typically 3 in production) provides redundancy
     * - Prevents data loss if brokers fail
     * - min.insync.replicas should be configured at the broker level (typically 2 in production)
     */
    @Bean
    public NewTopic eventTopic() {
        return TopicBuilder.name(KafkaConstants.TOPIC_NAME)
                .partitions(3) // Multiple partitions for parallel processing
                .replicas(1) // In production, use 3 for high availability
                .build();
    }

    /**
     * Creates a retry topic for handling failed message processing.
     *
     * CRITICAL SCENARIO: Error Handling with Retry Topics
     * - Separate topic for retrying failed messages
     * - Allows for different processing logic or delays for retry attempts
     * - Prevents failed messages from blocking the main processing queue
     * - Enables tracking of retry attempts separately from original messages
     */
    @Bean
    public NewTopic retryTopic() {
        return TopicBuilder.name(KafkaConstants.RETRY_TOPIC_NAME)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Creates a dead letter topic (DLT) for messages that fail processing after max retries.
     *
     * CRITICAL SCENARIO: Dead Letter Topic Pattern
     * - Critical for handling poison pills (messages that can never be processed successfully)
     * - Prevents endless retry loops that waste resources
     * - Allows for offline analysis of problematic messages
     * - Enables manual intervention or specialized processing for failed messages
     */
    @Bean
    public NewTopic dltTopic() {
        return TopicBuilder.name(KafkaConstants.DLT_TOPIC_NAME)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Configures the Kafka producer factory with critical settings for high throughput systems.
     *
     * CRITICAL SCENARIO: Producer Performance Tuning
     * - acks=all ensures data durability by waiting for all replicas to acknowledge
     * - retries and retry.backoff.ms handle transient network issues
     * - batch.size optimizes throughput by grouping messages
     * - linger.ms allows batching by waiting a short time before sending
     * - compression reduces network bandwidth usage
     * - buffer.memory prevents producer from being overwhelmed during broker unavailability
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();

        // Bootstrap servers - connection to Kafka cluster
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

        // Serializers for key and value
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // CRITICAL SCENARIO: Data Durability vs Throughput Tradeoff
        // acks=all ensures message is committed to all replicas before acknowledging
        // Provides highest durability but impacts throughput
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");

        // CRITICAL SCENARIO: Handling Transient Failures
        // Automatically retry failed sends (broker connection issues, leader elections, etc.)
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);

        // CRITICAL SCENARIO: Optimizing Throughput with Batching
        // Group messages into batches to reduce network overhead
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 5); // Wait 5ms to batch messages

        // CRITICAL SCENARIO: Network Bandwidth Optimization
        // Compress messages to reduce network bandwidth usage
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        // CRITICAL SCENARIO: Handling Broker Unavailability
        // Memory buffer for messages when brokers are unavailable
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432); // 32MB

        // CRITICAL SCENARIO: Preventing Duplicate Messages
        // Enable idempotent producer to prevent duplicates due to retries
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Creates a KafkaTemplate for sending messages to Kafka topics.
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
