package com.mimaraslan.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimaraslan.dto.AccountResponse;
import com.mimaraslan.dto.TransferRequest;
import com.mimaraslan.exception.DuplicateTransferException;
import com.mimaraslan.exception.UnauthorizedTransferException;
import com.mimaraslan.model.Ledger;
import com.mimaraslan.model.Transfer;
import com.mimaraslan.repository.LedgerRepository;
import com.mimaraslan.repository.TransferRepository;
import com.mimaraslan.util.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;
import io.micrometer.context.ContextSnapshot;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferInitService {

    private final LedgerRepository ledgerRepository;
    private final TransferRepository transferRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final JwtTokenUtil jwtTokenUtil;
    private final RestTemplate restTemplate;

    @Value("${ledger.account-service.base-url:http://localhost:80/account/}")
    private String accountServiceBaseUrl;

    @Transactional
    public void transferMoney(TransferRequest request) {
        log.info("Transfer request received: from={}, to={}, amount={}", 
                request.getFromAccount(), request.getToAccount(), request.getAmount());
        
        // Generate transferId if not provided
        if (request.getTransferId() == null) {
            request.setTransferId(UUID.randomUUID().toString());
        }
        
        // IDEMPOTENCY CHECK: Prevent duplicate transfers
        Optional<Transfer> existingTransfer = transferRepository.findByTransferId(request.getTransferId());
        if (existingTransfer.isPresent()) {
            Transfer existing = existingTransfer.get();
            String status = existing.getStatus();
            
            if ("SUCCESS".equals(status)) {
                log.warn("Duplicate transfer detected (already completed): transferId={}", request.getTransferId());
                throw new DuplicateTransferException(
                    "Transfer already completed with transferId: " + request.getTransferId()
                );
            }
            
            if ("PENDING".equals(status)) {
                log.info("Transfer already in progress: transferId={}, status={}", request.getTransferId(), status);
                // Transfer is already being processed, return without creating duplicate
                return;
            }
            
            if ("FRAUD".equals(status)) {
                log.warn("Transfer was previously marked as fraud: transferId={}", request.getTransferId());
                throw new DuplicateTransferException(
                    "Transfer was previously rejected due to fraud detection: " + request.getTransferId()
                );
            }
        }
        
        doTransfer(request);
    }

    protected void doTransfer(TransferRequest request) {
        Ledger fromAccount = ledgerRepository
                .findByAccountIbanNumber(request.getFromAccount())
                .orElseThrow(() -> new RuntimeException("Sender account not found: " + request.getFromAccount()));

        Ledger toAccount = ledgerRepository
                .findByAccountIbanNumber(request.getToAccount())
                .orElseThrow(() -> new RuntimeException("Receiver account not found"));

        // JWT Token'dan kullanıcı bilgilerini al ve gönderen hesabın sahibi ile doğrula
        validateTransferAuthorization(request.getAuthToken(), fromAccount.getAccountId());

        BigDecimal fromBalance = fromAccount.getBalance();
        BigDecimal amount = request.getAmount();

        request.setFromBalance(fromBalance);
        request.setFromAccountId(fromAccount.getAccountId());
        request.setToAccountId(toAccount.getAccountId());

        // Sadece transfer logunu oluştur; bakiye hareketi fraud sonucu aşamasında yapılır
        Transfer transfer = Transfer.builder()
                .fromAccountId(fromAccount.getAccountId())
                .fromAccountIban(fromAccount.getLedgerIbanNumber())
                .toAccountId(toAccount.getAccountId())
                .toAccountIban(toAccount.getLedgerIbanNumber())
                .amount(amount)
                .transferDate(LocalDateTime.now())
                .status("PENDING")
                .description("Waiting fraud check")
                .transferId(request.getTransferId())
              // .authToken(request.getAuthToken()) // Token'ı kaydet
                .build();

        transferRepository.save(transfer);

        // HTTP trace ile Kafka producer span'ini baglamak icin context'i commit oncesi yakala
        ContextSnapshot traceContext = ContextSnapshot.captureAll();

        // Transaction commit'ten sonra Kafka mesajı gönder (transfer kaydının görünür olması için)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try (ContextSnapshot.Scope scope = traceContext.setThreadLocals()) {
                    log.debug("Transaction committed, sending fraud check request: transferId={}, hasToken={}",
                            request.getTransferId(),
                            request.getAuthToken() != null && !request.getAuthToken().isEmpty());

                    String jsonMessage = objectMapper.writeValueAsString(request);
                    kafkaTemplate.send("fraud-check-events", jsonMessage);
                    log.info("Fraud check event sent to Kafka: transferId={}", request.getTransferId());
                } catch (JsonProcessingException e) {
                    log.error("Failed to send Kafka message after commit: {}", e.getMessage(), e);
                }
            }
        });
        
        log.debug("Transfer saved, Kafka message will be sent after transaction commit: transferId={}", 
                request.getTransferId());
    }

    /**
     * JWT token'dan email ve username alıp, gönderen hesabın sahibi ile doğrular
     * @param authToken JWT token (Bearer token formatında)
     * @param accountId Gönderen hesabın account ID'si
     * @throws UnauthorizedTransferException Token'daki kullanıcı hesap sahibi değilse
     */
    private void validateTransferAuthorization(String authToken, Long accountId) {
        if (authToken == null || authToken.isBlank()) {
            log.warn("No auth token provided for transfer validation");
            throw new UnauthorizedTransferException("Authentication token is required for transfer");
        }

        // Token'dan email ve username çıkar
        Optional<JwtTokenUtil.TokenInfo> tokenInfo = jwtTokenUtil.extractTokenInfo(authToken);
        
        if (tokenInfo.isEmpty()) {
            log.warn("Could not extract user information from token");
            throw new UnauthorizedTransferException("Invalid or expired token");
        }

        String tokenEmail = tokenInfo.get().getEmail();
        String tokenUsername = tokenInfo.get().getUsername();

        log.debug("Validating transfer authorization: accountId={}, tokenEmail={}, tokenUsername={}", 
                accountId, tokenEmail, tokenUsername);

        // AccountService'den hesap bilgilerini al
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, authToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<AccountResponse> response = restTemplate.exchange(
                    accountServiceBaseUrl + accountId,
                    HttpMethod.GET,
                    entity,
                    AccountResponse.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Failed to fetch account information: accountId={}, status={}", 
                        accountId, response.getStatusCode());
                throw new UnauthorizedTransferException("Account not found or access denied");
            }

            AccountResponse account = response.getBody();
            String accountEmail = account.getEmail();
            String accountUsername = account.getUsername();

            log.debug("Account info: accountId={}, accountEmail={}, accountUsername={}", 
                    accountId, accountEmail, accountUsername);

            // Email veya username kontrolü yap
            boolean emailMatch = tokenEmail != null && accountEmail != null 
                    && tokenEmail.equalsIgnoreCase(accountEmail);
            boolean usernameMatch = tokenUsername != null && accountUsername != null 
                    && tokenUsername.equalsIgnoreCase(accountUsername);

            if (!emailMatch && !usernameMatch) {
                log.warn("Transfer authorization failed: accountId={}, tokenEmail={}, accountEmail={}, " +
                        "tokenUsername={}, accountUsername={}", 
                        accountId, tokenEmail, accountEmail, tokenUsername, accountUsername);
                throw new UnauthorizedTransferException(
                    String.format("User %s/%s is not authorized to transfer from account %d (owner: %s/%s)",
                        tokenEmail != null ? tokenEmail : "N/A",
                        tokenUsername != null ? tokenUsername : "N/A",
                        accountId,
                        accountEmail != null ? accountEmail : "N/A",
                        accountUsername != null ? accountUsername : "N/A")
                );
            }

            log.info("Transfer authorization successful: accountId={}, user={}/{}", 
                    accountId, tokenEmail, tokenUsername);

        } catch (UnauthorizedTransferException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error validating transfer authorization: accountId={}, error={}", 
                    accountId, e.getMessage(), e);
            throw new UnauthorizedTransferException(
                "Failed to validate transfer authorization: " + e.getMessage()
            );
        }
    }
}
