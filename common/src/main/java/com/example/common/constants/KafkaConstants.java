package com.example.common.constants;

public class KafkaConstants {
    public static final String TOPIC_NAME = "long-running-events";
    public static final String RETRY_TOPIC_NAME = "long-running-events-retry";
    public static final String DLT_TOPIC_NAME = "long-running-events-dlt";
    public static final String GROUP_ID = "event-processor-group";
    public static final int MAX_RETRY_ATTEMPTS = 3;

    private KafkaConstants() {
        // Private constructor to prevent instantiation
    }
}
