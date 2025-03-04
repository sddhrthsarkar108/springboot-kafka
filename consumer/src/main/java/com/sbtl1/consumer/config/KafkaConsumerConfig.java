package com.sbtl1.consumer.config;

import com.example.common.constants.KafkaConstants;
import com.example.common.exception.RetryableException;
import org.apache.kafka.common.TopicPartition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    private static final Logger log = LogManager.getLogger(KafkaConsumerConfig.class);

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> template) {
        // Configure retry with exponential backoff
        ExponentialBackOffWithMaxRetries exponentialBackOff =
                new ExponentialBackOffWithMaxRetries(KafkaConstants.MAX_RETRY_ATTEMPTS);
        exponentialBackOff.setInitialInterval(1000L); // 1 second initial backoff
        exponentialBackOff.setMultiplier(2.0); // Double the wait time for each retry
        exponentialBackOff.setMaxInterval(10000L); // Maximum 10 seconds wait

        // Configure DLT for messages that fail after retries
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template, (record, exception) -> {
            // Send to retry topic if not exceeding max attempts, otherwise to DLT
            if (exception.getCause() instanceof RetryableException
                    && ((RetryableException) exception.getCause()).getAttempts() < KafkaConstants.MAX_RETRY_ATTEMPTS) {
                log.warn("Sending message to retry topic due to: {}", exception.getMessage());
                return new TopicPartition(KafkaConstants.RETRY_TOPIC_NAME, record.partition());
            }
            log.error("Sending message to DLT due to: {}", exception.getMessage());
            return new TopicPartition(KafkaConstants.DLT_TOPIC_NAME, record.partition());
        });

        // Use fixed backoff for simplicity in this example
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }
}
