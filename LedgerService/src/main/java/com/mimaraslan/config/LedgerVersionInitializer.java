package com.mimaraslan.config;

import com.mimaraslan.repository.LedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerVersionInitializer {

    private final LedgerRepository ledgerRepository;

    /**
     * On startup, ensure legacy rows have version initialized.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initVersions() {
        int updated = ledgerRepository.initializeVersionIfNull();
        if (updated > 0) {
            log.info("Ledger version initialized for {} rows (set to 0)", updated);
        }
    }
}
