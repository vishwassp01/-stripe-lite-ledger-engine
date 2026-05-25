package com.vishwas.ledger.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "ledger_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The parent transaction under which this credit/debit entry is recorded.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    // The account whose balance is modified by this journal entry.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    // DEBIT decreases asset accounts (or increases expense).
    // CREDIT increases asset accounts (or decreases expense).
    // In our system, debits subtract from account balance, credits add to it.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryType type;

    // Positive transfer amount.
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    public enum EntryType {
        DEBIT,
        CREDIT
    }
}
