package com.mimaraslan.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "ledgers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ledger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Hesap ID referansı
    private Long accountId;  // AccountService içindeki hesabın ID'si

    @Column(name = "ledger_iban_number", nullable = false, unique = true)
    private Long ledgerIbanNumber;

    private BigDecimal balance;

  //  @Column(nullable = false)
    private String currency; // "TL", "USD", "EUR"

    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long version;

}
