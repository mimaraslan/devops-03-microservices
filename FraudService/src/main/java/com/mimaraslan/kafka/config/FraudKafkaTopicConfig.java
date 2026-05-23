package com.mimaraslan.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FraudKafkaTopicConfig {

    @Bean
    public NewTopic fraudServiceEventsTopic() {
        return new NewTopic("fraud-service-events", 2, (short) 1);
        // 2 partition, replication factor 1
    }
}
