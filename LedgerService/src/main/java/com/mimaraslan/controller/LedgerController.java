package com.mimaraslan.controller;

import com.mimaraslan.dto.TransferRequest;
import com.mimaraslan.kafka.producer.AccountEventProducer;
import com.mimaraslan.model.Ledger;
import com.mimaraslan.service.LedgerService;
import com.mimaraslan.service.TransferInitService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/ledger")
public class LedgerController {

    private final AccountEventProducer producer;
    private final LedgerService ledgerService;
    private final TransferInitService transferInitService;

    @PostMapping("/transfer")
    public ResponseEntity<String> transferMoney(
            @RequestBody TransferRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        System.out.println("Transfer request: " + request.getFromAccount()
                + " -> " + request.getToAccount() + " Amount: " + request.getAmount());

        if (authHeader != null && !authHeader.isBlank()) {
            request.setAuthToken(authHeader);
        }

        transferInitService.transferMoney(request);
        return ResponseEntity.ok("Transfer requested");
    }

    @PostMapping
    public ResponseEntity<Ledger> createLedger(
            @RequestBody Ledger ledger,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        Ledger saved = ledgerService.createLedger(ledger, authHeader);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<Ledger>> getAccountsByAccount(@PathVariable Long accountId) {
        List<Ledger> ledgers = ledgerService.getLedgersByAccountId(accountId);
        return ResponseEntity.ok(ledgers);
    }


    @GetMapping("/account")
    public ResponseEntity<Page<Ledger>> getAccountsByAccountAccounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        Page<Ledger> accounts = ledgerService.getLedgersByAccountAccounts(page, size);
        return ResponseEntity.ok(accounts);
    }

}
