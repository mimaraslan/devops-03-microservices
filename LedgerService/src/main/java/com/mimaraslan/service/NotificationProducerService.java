package com.mimaraslan.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimaraslan.dto.AccountResponse;
import com.mimaraslan.dto.NotificationTransferEvent;
import com.mimaraslan.dto.TransferRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${ledger.account-service.base-url:http://localhost:80/account/}")
    private String accountServiceBaseUrl;

    public void sendTransferCompleted(TransferRequest request) {
        try {
            NotificationTransferEvent event = NotificationTransferEvent.builder()
                    .fromAccount(request.getFromAccount())
                    .toAccount(request.getToAccount())
                    .amount(request.getAmount())
                    .fromAccountId(request.getFromAccountId())
                    .toAccountId(request.getToAccountId())
                    .transferId(request.getTransferId())
                    .senderAccount(fetchAccount(request.getFromAccountId(), request.getAuthToken()))
                    .receiverAccount(fetchAccount(request.getToAccountId(), request.getAuthToken()))
                    .build();

            kafkaTemplate.send("notification-transfer-events", objectMapper.writeValueAsString(event));
            log.info("Notification event sent to Kafka: transferId={}", request.getTransferId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Notification send failed: " + e.getMessage(), e);
        }
    }

    private AccountResponse fetchAccount(Long accountId, String authToken) {
        if (accountId == null) {
            return null;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            if (authToken != null && !authToken.isBlank()) {
                headers.set(HttpHeaders.AUTHORIZATION,
                        authToken.startsWith("Bearer ") ? authToken : "Bearer " + authToken);
            }
            ResponseEntity<AccountResponse> response = restTemplate.exchange(
                    accountServiceBaseUrl + accountId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    AccountResponse.class
            );
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.warn("Could not fetch account {} for notification payload: {}", accountId, e.getMessage());
        }
        return null;
    }
}
