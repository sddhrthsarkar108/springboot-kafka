package com.sbtl1.producer.config;

import com.example.common.constants.KafkaConstants;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaProducerConfig {

    @Bean
    public NewTopic eventTopic() {
        return TopicBuilder.name(KafkaConstants.TOPIC_NAME)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic retryTopic() {
        return TopicBuilder.name(KafkaConstants.RETRY_TOPIC_NAME)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic dltTopic() {
        return TopicBuilder.name(KafkaConstants.DLT_TOPIC_NAME)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
