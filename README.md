# Spring Boot Kafka Long-Running Event Processing Demo

This project demonstrates how to handle long-running event processing with Kafka in a Spring Boot application, including failure handling, retry mechanisms, and exactly-once processing with Redis.

## Project Structure

- **Producer**: Sends events to Kafka
- **Consumer**: Processes events with simulated long-running tasks and failure handling
- **Common**: Shared models and constants

## Features

- Asynchronous event processing with CompletableFuture
- Simulated long-running tasks (2-5 seconds)
- Failure handling with retry mechanism
- Dead Letter Topic (DLT) for unprocessable messages
- Manual acknowledgment for better control
- Exactly-once processing with Redis
- Step-based processing with state tracking
- Transactional producers for atomic writes
- Log4j2 for logging instead of SLF4J

## Prerequisites

1. Install Docker & Docker Compose - `brew install --cask docker`
2. Install `asdf` - https://asdf-vm.com/guide/getting-started.html#official-download
3. Configure `asdf` as described in the original README

## Running the Application

### Using Docker (Recommended)

The application can be built and run using Docker Compose with a single command:

```shell
./gradlew dockerComposeUp
```

This will:
1. Build the application
2. Create Docker images for the producer and consumer
3. Start all services (Kafka, Zookeeper, Kafka UI, Redis, Redis Commander, Producer, Consumer)

To stop all services:

```shell
./gradlew dockerComposeDown
```

### Building Docker Images Manually

If you want to build the Docker images without starting the services:

```shell
./gradlew buildDockerImages
```

This will create Docker images for both the producer and consumer.

### Running Without Docker

If you prefer to run the application without Docker:

```shell
./gradlew clean build
java -jar producer/build/libs/producer-0.0.1-SNAPSHOT.jar  # Run Producer
java -jar consumer/build/libs/consumer-0.0.1-SNAPSHOT.jar  # Run Consumer
```

Note: You'll still need Kafka and Redis running, which you can start with:

```shell
docker-compose up -d kafka zookeeper kafka-ui redis redis-commander
```

## Testing the Application

Send a test event to the producer:

```shell
curl -X POST -H "Content-Type: text/plain" -d "This is a test event payload" http://localhost:8081/api/events
```

Test the transactional producer:

```shell
# Send a batch of events in a transaction
curl -X POST -H "Content-Type: application/json" -d '["Event 1", "Event 2", "Event 3"]' http://localhost:8081/api/transactional-events/batch

# Send events to multiple topics in a transaction
curl -X POST "http://localhost:8081/api/transactional-events/cross-topic?mainPayload=MainEvent&auditPayload=AuditEvent"

# Send event with coordinated database transaction
curl -X POST "http://localhost:8081/api/transactional-events/with-database?payload=EntityEvent&entityId=123"

# Test transaction rollback (should fail)
curl -X POST "http://localhost:8081/api/transactional-events/with-database?payload=EntityEvent&entityId=error"
```

## Kafka Topics

- `long-running-events`: Main topic for events
- `long-running-events-retry`: Retry topic for failed events
- `long-running-events-dlt`: Dead Letter Topic for unprocessable events

## Implementation Details

### Producer Patterns

The producer component demonstrates several critical patterns for high-throughput Kafka producers:

#### 1. Asynchronous Message Production

The producer sends messages asynchronously to avoid blocking threads, which is critical for high-throughput systems:

```java
public CompletableFuture<SendResult<String, Object>> sendEvent(String payload) {
    // Create event...
    
    return kafkaTemplate.send(record).whenComplete((result, ex) -> {
        if (ex == null) {
            log.info("Event sent successfully: {}, topic: {}, partition: {}, offset: {}",
                    event.getId(),
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } else {
            log.error("Unable to send event: {}, error: {}", event.getId(), ex.getMessage(), ex);
        }
    });
}
```

#### 2. Message Key Selection Strategy

Using a consistent key ensures related events go to the same partition, enabling ordering guarantees:

```java
ProducerRecord<String, Object> record = new ProducerRecord<>(
        KafkaConstants.TOPIC_NAME,
        null, // Let Kafka assign the partition based on the key
        System.currentTimeMillis(), // Timestamp
        event.getId().toString(), // Key - ensures related events go to same partition
        event // Value
);
```

#### 3. Message Headers for Metadata

Headers provide additional context and metadata for tracking and debugging:

```java
record.headers().add(new RecordHeader("source", "event-producer".getBytes()));
record.headers().add(new RecordHeader("event-type", "standard".getBytes()));
record.headers().add(new RecordHeader("trace-id", UUID.randomUUID().toString().getBytes()));
```

#### 4. Rate Limiting and Backpressure

Controlling the rate of message production prevents overwhelming consumers:

```java
// Send messages with a controlled rate to prevent overwhelming the broker
int messageCount = 0;
for (String payload : payloads) {
    // Add a small delay every 100 messages to prevent overwhelming the broker
    if (messageCount > 0 && messageCount % 100 == 0) {
        try {
            // Implement backpressure with a small delay
            TimeUnit.MILLISECONDS.sleep(50);
            log.info("Applied rate limiting after {} messages", messageCount);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Rate limiting interrupted", e);
        }
    }

    // Send the message and collect the future
    futures.add(sendEvent(payload));
    messageCount++;
}
```

#### 5. Transactional Producer Pattern

The transactional producer ensures atomic writes across multiple topics and coordinates with database transactions:

```java
@Transactional
public CompletableFuture<Void> sendEventsInTransaction(List<String> payloads) {
    log.info("Starting Kafka transaction for {} events", payloads.size());

    // Start a Kafka transaction
    kafkaTemplate.executeInTransaction(operations -> {
        for (String payload : payloads) {
            // Create event...
            
            // Send within the transaction
            operations.send(record);
        }
        return null;
    });

    log.info("Kafka transaction committed for {} events", payloads.size());
    return CompletableFuture.completedFuture(null);
}
```

Cross-topic transactions ensure consistency across multiple topics:

```java
@Transactional
public CompletableFuture<Void> sendCrossTopicTransaction(String mainTopicPayload, String auditTopicPayload) {
    // Start a Kafka transaction
    kafkaTemplate.executeInTransaction(operations -> {
        // Create main event...
        // Create audit event...
        
        // Send main event
        operations.send(KafkaConstants.TOPIC_NAME, mainEvent.getId().toString(), mainEvent);

        // Send audit event to a different topic
        operations.send(KafkaConstants.RETRY_TOPIC_NAME, auditEvent.getId().toString(), auditEvent);

        return null;
    });
    
    return CompletableFuture.completedFuture(null);
}
```

Coordinating database and Kafka transactions:

```java
@Transactional
public CompletableFuture<Void> sendWithDatabaseTransaction(String payload, String entityId) {
    try {
        // Simulate database operation
        simulateDatabaseOperation(entityId);

        // Create event...
        
        // Send event within the transaction
        kafkaTemplate.send(KafkaConstants.TOPIC_NAME, event.getId().toString(), event);

        return CompletableFuture.completedFuture(null);
    } catch (Exception e) {
        // Both database and Kafka transactions will be rolled back
        log.error("Error in coordinated transaction, rolling back: {}", e.getMessage(), e);
        throw e;
    }
}
```

### Consumer Patterns

The consumer component demonstrates several critical patterns for high-throughput Kafka consumers:

#### 1. Manual Acknowledgment Pattern

Using manual acknowledgment provides precise control over offset commits:

```java
@KafkaListener(topics = KafkaConstants.TOPIC_NAME, groupId = KafkaConstants.GROUP_ID)
public void consumeEvent(ConsumerRecord<String, Event> record, Acknowledgment acknowledgment) {
    Event event = record.value();
    log.info("Received event: {} from topic: {}, partition: {}, offset: {}",
            event.getId(), record.topic(), record.partition(), record.offset());
    
    try {
        // Acknowledge the message immediately to prevent redelivery
        // This implements an "at-most-once" delivery pattern
        acknowledgment.acknowledge();
        
        // Process the event...
    } catch (Exception e) {
        // Handle exceptions...
    }
}
```

#### 2. Exactly-Once Processing with Redis

Using Redis to track processed events enables exactly-once processing in a distributed environment:

```java
// Check if we've already processed this event using Redis with TTL
String eventKey = event.getId().toString();

// If the event is already fully processed, skip it
if (processedEventTracker.isEventProcessed(eventKey)) {
    log.info("Event {} already fully processed, skipping", event.getId());
    return;
}

// If the event is currently being processed (in-progress), skip it
if (processedEventTracker.isEventBeingProcessed(eventKey)) {
    log.info("Event {} is currently being processed, skipping duplicate", event.getId());
    return;
}

// Mark the event as being processed in Redis with TTL
if (!processedEventTracker.markEventAsProcessing(eventKey)) {
    log.info("Event {} was marked as processing by another instance, skipping", event.getId());
    return;
}
```

The `ProcessedEventTracker` service uses Redis with TTL to track event processing status:

```java
public boolean markEventAsProcessing(String eventId) {
    String key = KEY_PREFIX + eventId;

    // Use Redis SETNX (SET if Not eXists) for atomic check-and-set
    Boolean isNew = redisTemplate.opsForValue().setIfAbsent(
            key, "PROCESSING", Duration.ofHours(processedEventsTtlHours));

    if (Boolean.TRUE.equals(isNew)) {
        log.debug("Marked event {} as processing with TTL of {} hours", eventId, processedEventsTtlHours);
        return true;
    } else {
        log.debug("Event {} is already being processed", eventId);
        return false;
    }
}
```

#### 3. Asynchronous Processing Pattern

Processing events asynchronously improves throughput by not blocking the consumer thread:

```java
eventProcessingService
        .processEvent(event)
        .thenAccept(processedEvent -> {
            if (processedEvent.isProcessed()) {
                // Handle successful processing...
                processedEventTracker.markEventAsProcessed(eventKey);
            } else {
                // Handle failed processing...
                processedEventTracker.removeEvent(eventKey);
                kafkaTemplate.send(KafkaConstants.DLT_TOPIC_NAME, 
                        processedEvent.getId().toString(), processedEvent);
            }
        })
        .exceptionally(e -> {
            // Handle exceptions...
            return null;
        });
```

#### 4. Retry Pattern with Backoff

Implementing retry with exponential backoff for transient failures:

```java
@KafkaListener(topics = KafkaConstants.RETRY_TOPIC_NAME, groupId = KafkaConstants.GROUP_ID)
public void consumeRetryEvent(ConsumerRecord<String, Event> record, Acknowledgment acknowledgment) {
    Event event = record.value();
    StepStatus currentStepStatus = event.getCurrentStepStatus();
    
    // Implement backoff delay before processing retry
    try {
        // Simple exponential backoff: 1s, 2s, 4s, etc.
        int attempts = currentStepStatus != null ? currentStepStatus.getAttempts() : 1;
        long backoffMs = (long) Math.pow(2, attempts - 1) * 1000;
        log.info("Applying backoff delay of {}ms before processing retry for event: {}", 
                backoffMs, event.getId());
        Thread.sleep(backoffMs);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("Backoff interrupted for event: {}", event.getId());
    }
    
    // Process the retry event the same way as a regular event
    consumeEvent(record, acknowledgment);
}
```

#### 5. Dead Letter Topic Pattern

Sending unprocessable messages to a Dead Letter Topic (DLT) for later analysis:

```java
if (re.getAttempts() < KafkaConstants.MAX_RETRY_ATTEMPTS) {
    // If we haven't exceeded max retries, send to retry topic
    kafkaTemplate.send(
            KafkaConstants.RETRY_TOPIC_NAME,
            event.getId().toString(),
            event);
} else {
    // Otherwise, send to DLT after max retries
    event.setErrorMessage("Max retry attempts exceeded for step "
            + (failedStep != null ? failedStep.name() : "unknown")
            + ": " + e.getMessage());
    kafkaTemplate.send(
            KafkaConstants.DLT_TOPIC_NAME,
            event.getId().toString(),
            event);
}
```

### Processing Patterns

The processing component demonstrates several critical patterns for high-throughput event processing:

#### 1. Step-Based Processing with State Tracking

Breaking complex processing into discrete, manageable steps with state tracking:

```java
public CompletableFuture<Event> processEvent(Event event) {
    // Initialize step statuses if not already initialized
    if (event.getStepStatuses() == null || event.getStepStatuses().isEmpty()) {
        event.initializeStepStatuses();
    }
    
    // If no current step is set, start with the first step
    if (event.getCurrentStep() == null) {
        event.setCurrentStep(ProcessingStep.getFirstStep());
    }
    
    return CompletableFuture.supplyAsync(
            () -> {
                try {
                    // Process the current step
                    return processCurrentStep(event);
                } catch (InterruptedException e) {
                    // Handle interruption...
                }
            },
            processingExecutor);
}
```

The `Event` model maintains the processing state:

```java
public class Event {
    private UUID id;
    private String payload;
    private LocalDateTime timestamp;
    private int processingAttempts;
    private boolean processed;
    private String errorMessage;
    private ProcessingStep currentStep;
    private List<StepStatus> stepStatuses = new ArrayList<>();
    
    // Methods for state management...
}
```

#### 2. Dedicated Thread Pools for Different Types of Operations

Using separate thread pools for CPU-bound and I/O-bound operations:

```java
// Dedicated thread pool for CPU-bound processing tasks
private final Executor processingExecutor =
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

// Separate thread pool for I/O-bound operations (database, external services)
private final Executor ioExecutor = Executors.newFixedThreadPool(20);
```

#### 3. Circuit Breaking for External Services

Implementing circuit breaking to handle external service failures:

```java
// Simulate occasional external service failures (30% chance)
if (random.nextInt(10) < 3) {
    // This type of failure is temporary and can be retried
    String errorMsg = "External service unavailable for enrichment";
    log.warn(errorMsg + " for event: {}", event.getId());

    stepStatus.setState(StepState.FAILED);
    stepStatus.setEndTime(LocalDateTime.now());
    stepStatus.setErrorMessage(errorMsg);

    throw new RetryableException(errorMsg, stepStatus.getAttempts(), stepStatus.getStep());
}
```

#### 4. Distributed State Management with Redis

Using Redis for distributed state management with TTL:

```java
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use StringRedisSerializer for both keys and values
        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);

        return template;
    }
}
```

Redis configuration in `application.properties`:

```properties
# Redis Configuration for Exactly-Once Processing
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.timeout=2000
spring.redis.database=0

# TTL for processed events in Redis (in hours)
kafka.processed-events.ttl-hours=24
```

## Additional Kafka Concepts for Distributed High-Throughput Event Processing

### 1. Transactional Outbox Pattern

The Transactional Outbox Pattern ensures reliable message delivery by combining database transactions with message publishing:

1. Store outgoing messages in a database table as part of the business transaction
2. A separate process reads the outbox table and publishes messages to Kafka
3. Mark messages as published after successful delivery

This pattern solves the dual-write problem and ensures consistency between database state and published messages.

### 2. Transactional Producers

Transactional producers in Kafka provide atomic writes across multiple topics and messages:

1. **Atomic Batch Writes**: All messages in a batch are either committed or aborted
2. **Cross-Topic Consistency**: Ensures consistency across multiple topics
3. **Exactly-Once Semantics**: When combined with EOS consumers
4. **Database Coordination**: Can be integrated with database transactions

Configuration required for transactional producers:

```properties
spring.kafka.producer.transaction-id-prefix=tx-
spring.kafka.producer.properties.enable.idempotence=true
spring.kafka.producer.properties.max.in.flight.requests.per.connection=5
```

### 3. Partition Assignment Strategies

Kafka offers several partition assignment strategies that can be optimized for different use cases:

- **RangeAssignor**: Assigns partitions on a per-topic basis (default)
- **RoundRobinAssignor**: Assigns partitions across all topics in a round-robin fashion
- **StickyAssignor**: Minimizes partition movement when consumers join or leave
- **CooperativeStickyAssignor**: Allows for cooperative rebalancing without stopping consumption

Example configuration:

```properties
spring.kafka.consumer.properties.partition.assignment.strategy=org.apache.kafka.clients.consumer.CooperativeStickyAssignor
```

### 4. Consumer Lag Monitoring

Monitoring consumer lag (the difference between the latest produced offset and the consumed offset) is critical for high-throughput systems:

1. Use Kafka's built-in metrics to track consumer lag
2. Set up alerts for excessive lag
3. Implement auto-scaling based on lag metrics

### 5. Compacted Topics

For use cases that only care about the latest value for a key, compacted topics can significantly improve efficiency:

```properties
cleanup.policy=compact
min.compaction.lag.ms=60000
delete.retention.ms=600000
```

### 6. Schema Evolution with Schema Registry

For evolving message schemas without breaking consumers:

1. Use a schema registry to manage and validate schemas
2. Implement forward and backward compatibility
3. Use Avro, Protobuf, or JSON Schema for structured messages

### 7. Consumer Concurrency Models

Different concurrency models for scaling consumer processing:

1. **Multiple Consumer Instances**: Horizontal scaling across machines
2. **Multiple Threads per Consumer**: Vertical scaling within a single instance
3. **Async Processing**: Non-blocking processing of messages

Our implementation uses a combination of these approaches for optimal throughput.

## Failure Handling

The application demonstrates several failure scenarios:

1. **Retryable Failures**: Events that fail but can be retried are sent to the retry topic
2. **Permanent Failures**: Events that exceed the maximum retry attempts are sent to the DLT
3. **Timeout Failures**: Events that take too long to process are sent to the retry topic
4. **Interrupted Processing**: Events whose processing is interrupted are nacked for redelivery
5. **Duplicate Messages**: Handled by the Redis-based exactly-once processing mechanism