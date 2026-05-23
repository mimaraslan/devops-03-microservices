package com.mimaraslan.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic ledgerServiceTransferEventsTopic() {
        // Topic adı, partition sayısı, replication factor
        return new NewTopic("ledger-service-transfer-events", 2, (short) 1);
    }
}
