package com.mimaraslan.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationKafkaTopicConfig {

    @Bean
    public NewTopic notificationServiceEventsTopic() {
        return new NewTopic("notification-service-events", 2, (short) 1);
        // 2 partition, replication factor 1
    }
}
