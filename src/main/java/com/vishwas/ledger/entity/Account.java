package com.vishwas.ledger.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "accounts", indexes = {
        @Index(name = "idx_account_name", columnList = "name", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Account identifier (e.g., "User_A_Savings", "Stripe_Escrow").
    // Must be unique for clean, reliable double-entry journal mappings.
    @Column(nullable = false, unique = true)
    private String name;

    // BigDecimals must always be used for monetary fields in Java.
    // Floating-point values (float/double) lose precision during operations, which is fatal in financial systems.
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    // JPA Optimistic Locking Version.
    // If two threads try to debit this account simultaneously, Hibernate checks this version.
    // The second thread's update will fail because the version changed, avoiding race condition overdrafts.
    @Version
    @Column(nullable = false)
    private Long version;
}
