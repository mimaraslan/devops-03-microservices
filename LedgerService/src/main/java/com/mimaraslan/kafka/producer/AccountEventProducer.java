package com.mimaraslan.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimaraslan.dto.TransferRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class AccountEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;


    public void sendAccountEventMulti(String message) {
        // "account-events" topic'ine mesaj gönderiyoruz
        for (int i = 1; i < 5; i++) {
            kafkaTemplate.send("ledger-service-transfer-events", message + " ---->>>> "+i);
            System.out.println("Sent message: " + message + " ---->>>> "+i);
        }
    }


    public void sendAccountEventOne(TransferRequest request) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(request);
            kafkaTemplate.send("ledger-service-transfer-events", jsonMessage);
            System.out.println("Sent message: " + jsonMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



}
