package com.mimaraslan.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimaraslan.dto.TransferRequest;
import com.mimaraslan.dto.FraudCheckResponse;
import com.mimaraslan.service.FraudService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FraudConsumer {

    private final FraudService fraudService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;


    @KafkaListener(
            topics = "fraud-check-events",
            groupId = "fraud-service-group"
    )
    @RetryableTopic(
            attempts = "5",  // maksimum 5 deneme
            backoff = @Backoff(
                    delay = 2000,  // ilk deneme gecikmesi 2 saniye
                    multiplier = 2) // her retry’de gecikmeyi 2 kat artır
    )
    @CircuitBreaker(name = "fraudService", fallbackMethod = "fallbackFraudDetected")
    public void consume(String message) throws Exception {

        TransferRequest transfer = objectMapper.readValue(message, TransferRequest.class);

        log.info("Fraud check request received: transferId={}, from={}, to={}, amount={}", 
                transfer.getTransferId(), transfer.getFromAccount(), transfer.getToAccount(), transfer.getAmount());

        boolean fraudResult = fraudService.checkFraud(transfer);

        log.info("Fraud check completed: transferId={}, isFraud={}", transfer.getTransferId(), fraudResult);

        // Sonucu LedgerService'e gönder (token'ı da taşı)
        String resultMessage = objectMapper.writeValueAsString(
                FraudCheckResponse.builder()
                        .fraud(fraudResult)
                        .transferId(transfer.getTransferId())
                        .message(fraudResult ? "Amount too high" : "No fraud")
                        .authToken(transfer.getAuthToken()) // Token'ı Kafka üzerinden taşı
                        .build()
        );

        kafkaTemplate.send("fraud-result-events", resultMessage);
        log.info("Fraud check result sent to Kafka: transferId={}, hasToken={}", 
                transfer.getTransferId(), transfer.getAuthToken() != null && !transfer.getAuthToken().isEmpty());

    }


    // fallback signature: (same parameters) + Throwable
    public void fallbackFraudDetected(String message, Throwable t) {
        System.out.println("[FraudService] Fallback: " + message
                + " | Reason: " + t.getMessage());
    }


}
