package com.sbtl1.consumer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis Configuration
 *
 * This class configures Redis for use as a distributed cache in a high-throughput Kafka consumer application.
 * Redis provides a scalable, distributed solution for tracking processed events with TTL support.
 */
@Configuration
public class RedisConfig {

    /**
     * Creates a RedisTemplate for string operations.
     *
     * CRITICAL SCENARIO: Distributed State Management
     * - Uses Redis as a distributed cache for tracking processed events
     * - Enables exactly-once processing across multiple consumer instances
     * - Provides TTL (Time-To-Live) support for automatic cleanup of old entries
     * - Scales horizontally with the application
     *
     * @param connectionFactory The Redis connection factory
     * @return RedisTemplate configured for string operations
     */
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
