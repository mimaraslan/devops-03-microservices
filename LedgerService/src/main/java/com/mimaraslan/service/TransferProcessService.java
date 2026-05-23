package com.mimaraslan.service;

import com.mimaraslan.dto.TransferRequest;
import com.mimaraslan.exception.InsufficientBalanceException;
import com.mimaraslan.exception.OptimisticLockException;
import com.mimaraslan.model.Ledger;
import com.mimaraslan.model.Transfer;
import com.mimaraslan.repository.LedgerRepository;
import com.mimaraslan.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferProcessService {

    private final LedgerRepository accountRepository;
    private final TransferRepository transferRepository;
    private final NotificationProducerService notificationProducerService;

    // FraudService'den gelen sonucu işlemek için method
    @Transactional
    public void processFraudResult(TransferRequest request, boolean isFraud) {

        log.info("Processing fraud result kafka: transferId={}, isFraud={}", request.getTransferId(), isFraud);

        Transfer transferLog = null;
        if (request.getTransferId() != null) {
            // Mevcut transfer kaydını al
            transferLog = transferRepository.findByTransferId(request.getTransferId())
                    .orElseThrow(() -> new RuntimeException("Transfer record not found"));
            log.debug("Transfer log found: transferId={}, status={}", transferLog.getTransferId(), transferLog.getStatus());
        }

        if (isFraud) {
            // Fraud detected
            transferLog.setTransferDate(LocalDateTime.now());
            transferLog.setStatus("FRAUD");
            transferLog.setDescription("Fraud detected");
            transferRepository.save(transferLog);
            log.warn("[LedgerService] Fraud detected, transfer rejected: transferId={}", request.getTransferId());
            return;
        }

        // Sender - Get accounts with current versions for optimistic locking
        Ledger fromAccount = accountRepository
                .findByAccountIbanNumber(request.getFromAccount())
                .orElseThrow(() -> new RuntimeException("Sender account not found"));

        // Receiver - Get accounts with current versions for optimistic locking
        Ledger toAccount = accountRepository
                .findByAccountIbanNumber(request.getToAccount())
                .orElseThrow(() -> new RuntimeException("Receiver account not found"));

        BigDecimal amount = request.getAmount();
        BigDecimal fromBalance = fromAccount.getBalance();
        Long fromVersion = fromAccount.getVersion();
        Long toVersion = toAccount.getVersion();

        // Balance validation
        if (fromBalance.compareTo(amount) < 0) {
            log.error("Insufficient balance: account={}, balance={}, amount={}", 
                    request.getFromAccount(), fromBalance, amount);
            transferLog.setStatus("FAILED");
            transferLog.setDescription("Insufficient balance");
            transferRepository.save(transferLog);
            throw new InsufficientBalanceException(
                String.format("Insufficient balance. Available: %s, Required: %s", fromBalance, amount)
            );
        }

        // ATOMIC TRANSFER using Optimistic Locking
        // Decrement from account balance (with version check)
        int fromUpdated = accountRepository.decrementBalance(
                request.getFromAccount(), 
                amount, 
                fromVersion
        );

        if (fromUpdated == 0) {
            log.error("Optimistic lock failed for fromAccount: iban={}, version={}", 
                    request.getFromAccount(), fromVersion);
            transferLog.setStatus("FAILED");
            transferLog.setDescription("Concurrent modification detected");
            transferRepository.save(transferLog);
            throw new OptimisticLockException(
                "Account balance was modified by another transaction. Please retry."
            );
        }

        // Increment to account balance (with version check)
        int toUpdated = accountRepository.incrementBalance(
                request.getToAccount(), 
                amount, 
                toVersion
        );

        // bakiye transferi sırasında concurrent modification (eşzamanlı değişiklik) problemlerini önlemek için
        if (toUpdated == 0) {
            log.error("Optimistic lock failed for toAccount: iban={}, version={}", 
                    request.getToAccount(), toVersion);
            // Rollback: increment from account back
            accountRepository.incrementBalance(request.getFromAccount(), amount, fromVersion + 1);
            transferLog.setStatus("FAILED");
            transferLog.setDescription("Concurrent modification detected on receiver account");
            transferRepository.save(transferLog);
            throw new OptimisticLockException(
                "Receiver account was modified by another transaction. Transfer rolled back. Please retry."
            );
        }

        // TRANSFER SUCCESS
        transferLog.setTransferDate(LocalDateTime.now());
        transferLog.setStatus("SUCCESS");
        transferLog.setDescription("Money transferred successfully");
        transferRepository.save(transferLog);

        log.info("[LedgerService] Transfer completed successfully: transferId={}, amount={}", 
                request.getTransferId(), amount);

        // Kafka notification (token zaten request'te var, Kafka'dan geldi)
        // Token database'de değil, Kafka mesajlarında taşınıyor
        notificationProducerService.sendTransferCompleted(request);
    }
}
