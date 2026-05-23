package com.mimaraslan.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimaraslan.dto.FraudCheckResponse;
import com.mimaraslan.dto.TransferRequest;
import com.mimaraslan.model.Transfer;
import com.mimaraslan.repository.LedgerRepository;
import com.mimaraslan.repository.TransferRepository;
import com.mimaraslan.service.TransferProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudResultConsumerService {

    private final ObjectMapper objectMapper;
    private final TransferRepository transferRepository;
    private final TransferProcessService transferProcessService;
    private final LedgerRepository accountRepository;

    @KafkaListener(topics = "fraud-result-events", groupId = "account-group")
    @Transactional
    public void handleFraudResult(String message) {
        try {
            FraudCheckResponse response = objectMapper.readValue(message, FraudCheckResponse.class);

            log.info("Fraud result received: transferId={}, isFraud={}, hasToken={}", 
                    response.getTransferId(), response.isFraud(), 
                    response.getAuthToken() != null && !response.getAuthToken().isEmpty());

            // Transfer ID üzerinden transfer kaydını bul
            Transfer transfer = transferRepository.findByTransferId(response.getTransferId())
                    .orElseThrow(() -> new RuntimeException("Transfer not found with id=" + response.getTransferId()));

            // TransferRequest oluştur (token'ı FraudCheckResponse'dan al, database'den değil)
            TransferRequest request = TransferRequest.builder()
                    .fromAccount(transfer.getFromAccountIban())
                    .toAccount(transfer.getToAccountIban())
                    .amount(transfer.getAmount())
                    .fromAccountId(transfer.getFromAccountId())
                    .toAccountId(transfer.getToAccountId())
                    .transferId(response.getTransferId())
                    .authToken(response.getAuthToken()) // Token'ı Kafka mesajından al (database'den değil)
                    .fromBalance(accountRepository.findByAccountIbanNumber(transfer.getFromAccountIban()).get().getBalance())
                    .build();

            log.debug("TransferRequest created for processing: transferId={}, hasToken={}", 
                    request.getTransferId(), request.getAuthToken() != null && !request.getAuthToken().isEmpty());

            transferProcessService.processFraudResult(request, response.isFraud());

        } catch (Exception e) {
            log.error("Error processing fraud result: {}", e.getMessage(), e);
        }
    }
}
