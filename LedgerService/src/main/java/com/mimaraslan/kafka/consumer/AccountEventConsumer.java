package com.mimaraslan.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimaraslan.dto.TransferRequest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AccountEventConsumer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(
            topics = "ledger-service-transfer-events",
            groupId = "ledger-service-group"
    )
    @RetryableTopic(
            attempts = "5",  // maksimum 5 deneme
            backoff = @Backoff(
                    delay = 2000,  // ilk deneme gecikmesi 2 saniye
                    multiplier = 2) // her retry’de gecikmeyi 2 kat artır
    )
    @Async
    public void consume(String message) {

        System.out.println("Received message: " + message);
        // Burada mesaj işleme kodu yapiliyor.

        try {
            // Kafka mesajını JSON olarak parse et
            TransferRequest transfer = objectMapper.readValue(message, TransferRequest.class);

            // Konsola log
            System.out.println("Received transfer event:");
            System.out.println("From: " + transfer.getFromAccount());
            System.out.println("To: " + transfer.getToAccount());
            System.out.println("Amount: " + transfer.getAmount());

            // Burada FraudService / LedgerService / NotificationService için async call yapılabilir

        } catch (Exception e) {
            System.err.println("Failed to parse message: " + message);
            e.printStackTrace();
        }

    }
}
