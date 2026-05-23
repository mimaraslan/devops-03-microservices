package com.mimaraslan.controller;

import com.mimaraslan.model.Transfer;
import com.mimaraslan.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/transfer")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @GetMapping
    public ResponseEntity<Page<Transfer>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        Page<Transfer> transfers = transferService.findAll(page, size);
        return ResponseEntity.ok(transfers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transfer> findById(@PathVariable Long id) {
        return ResponseEntity.ok(transferService.findById(id));
    }

    @GetMapping("/account/iban/{iban}")
    public ResponseEntity<List<Transfer>> getTransfersByAccountIban(@PathVariable String iban) {
        return ResponseEntity.ok(transferService.findByAccountIban(iban));
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<Transfer>> getTransfersByAccountId(@PathVariable Long accountId) {
        return ResponseEntity.ok(transferService.findByAccountId(accountId));
    }
}
