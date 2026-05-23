package com.mimaraslan.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimaraslan.dto.TransferRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FraudProducerService {


    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;


    public void sendFraudEvent(TransferRequest request) {
        try {
            kafkaTemplate.send("fraud-check-events", objectMapper.writeValueAsString(request));
            System.out.println("[LedgerService] Fraud check event sent to Kafka");
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Kafka fraud send failed: " + e.getMessage());
        }
    }
}