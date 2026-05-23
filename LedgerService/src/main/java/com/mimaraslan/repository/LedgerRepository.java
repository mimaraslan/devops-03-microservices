package com.mimaraslan.repository;

import com.mimaraslan.model.Ledger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface LedgerRepository extends JpaRepository <Ledger, Long> {

    List<Ledger> findByAccountId(Long accountId);


    @Query("SELECT a FROM Ledger a WHERE a.ledgerIbanNumber = :iban")
    Optional<Ledger> findByAccountIbanNumber(Long iban);


    // Decrement Balance (Gönderen Hesap)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Ledger a SET a.balance = a.balance - :amount, a.version = a.version + 1 " +
            "WHERE a.ledgerIbanNumber = :iban AND a.balance >= :amount AND a.version = :version")
    int decrementBalance(@Param("iban") Long iban,
                         @Param("amount") BigDecimal amount,
                         @Param("version") Long version);


    // Increment Balance (Alıcı Hesap)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Ledger a SET a.balance = a.balance + :amount, a.version = a.version + 1 " +
            "WHERE a.ledgerIbanNumber = :iban AND a.version = :version")
    int incrementBalance(@Param("iban") Long iban,
                         @Param("amount") BigDecimal amount,
                         @Param("version") Long version);


    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Ledger a SET a.version = 0 WHERE a.version IS NULL")
    int initializeVersionIfNull();

}
