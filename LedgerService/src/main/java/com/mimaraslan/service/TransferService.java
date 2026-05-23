package com.mimaraslan.service;

import com.mimaraslan.model.Transfer;
import com.mimaraslan.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferRepository transferRepository;

    public Page<Transfer> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return transferRepository.findAll(pageable);
    }

    public Transfer findById(Long id) {
        return transferRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transfer not found. id=" + id));
    }

    public List<Transfer> findByAccountIban(String iban) {
        return transferRepository.findAll().stream()
                .filter(t -> t.getFromAccountIban().equals(iban)
                        || t.getToAccountIban().equals(iban))
                .toList();
    }

    public List<Transfer> findByAccountId(Long accountId) {
        return transferRepository.findAll().stream()
                .filter(t -> t.getFromAccountId().equals(accountId)
                        || t.getToAccountId().equals(accountId))
                .toList();
    }
}
