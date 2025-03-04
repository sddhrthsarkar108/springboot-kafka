# Spring Boot Kafka Long-Running Event Processing Demo

This project demonstrates how to handle long-running event processing with Kafka in a Spring Boot application, including failure handling and retry mechanisms.

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
3. Start all services (Kafka, Zookeeper, Kafka UI, Producer, Consumer)

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

Note: You'll still need Kafka running, which you can start with:

```shell
docker-compose up -d kafka zookeeper kafka-ui
```

## Testing the Application

Send a test event to the producer:

```shell
curl -X POST -H "Content-Type: text/plain" -d "This is a test event payload" http://localhost:32000/producer/api/events
```

## Kafka Topics

- `long-running-events`: Main topic for events
- `long-running-events-retry`: Retry topic for failed events
- `long-running-events-dlt`: Dead Letter Topic for unprocessable events

## Implementation Details

### Producer

- REST API to accept event requests
- Kafka producer to send events to the main topic
- Log4j2 for logging

### Consumer

- Kafka consumer to process events from the main topic
- Long-running event processing with simulated delays
- Failure handling with retry mechanism
- Dead Letter Topic for unprocessable messages
- Log4j2 for logging

## Failure Handling

The application demonstrates several failure scenarios:

1. **Retryable Failures**: Events that fail but can be retried are sent to the retry topic
2. **Permanent Failures**: Events that exceed the maximum retry attempts are sent to the DLT
3. **Timeout Failures**: Events that take too long to process are sent to the retry topic
4. **Interrupted Processing**: Events whose processing is interrupted are nacked for redelivery