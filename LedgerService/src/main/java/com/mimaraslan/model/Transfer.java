package com.mimaraslan.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transfers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long fromAccountId;
    private Long fromAccountIban;

    private Long toAccountId;
    private Long toAccountIban;

    private BigDecimal amount;

    private LocalDateTime transferDate;
    private String transferId;

    private String status;     // SUCCESS, FAILED, FRAUD
    private String description;

}
