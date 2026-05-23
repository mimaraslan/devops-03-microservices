package com.mimaraslan.service;

import com.mimaraslan.model.Ledger;
import com.mimaraslan.repository.LedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerRepository ledgerRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ledger.account-service.base-url:http://localhost:80/account/}")
    private String accountServiceBaseUrl;

    @Retryable(
        retryFor = {RestClientException.class, RuntimeException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Ledger createLedger(Ledger ledger, String authHeader) {
        log.info("Validating account existence: accountId={}", ledger.getAccountId());

        // Hesap var mı LedgerService tarafında (Account endpoint'inden) doğrula
        HttpHeaders headers = new HttpHeaders();
        if (authHeader != null && !authHeader.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        }
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<Object> response = restTemplate.exchange(
                    accountServiceBaseUrl + ledger.getAccountId(),
                    HttpMethod.GET,
                    entity,
                    Object.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Account check failed with status: " + response.getStatusCode());
            }
            log.debug("Account validation successful: accountId={}", ledger.getAccountId());
        } catch (RestClientException e) {
            log.error("Account validation failed (retryable): accountId={}, error={}", 
                    ledger.getAccountId(), e.getMessage());
            throw new RuntimeException("Account not found for ledger operation! accountId=" + ledger.getAccountId(), e);
        }

        return ledgerRepository.save(ledger);
    }

    public java.util.List<Ledger> getLedgersByAccountId(Long accountId) {
        return ledgerRepository.findByAccountId(accountId);
    }


    public Page<Ledger> getLedgersByAccountAccounts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ledgerRepository.findAll(pageable);
    }

}