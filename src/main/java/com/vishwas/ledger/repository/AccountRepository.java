package com.vishwas.ledger.repository;

import com.vishwas.ledger.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    // Retrieve account details by its unique logical name (e.g. "User_A_Savings").
    // Used to load balances prior to processing transfer transactions.
    Optional<Account> findByName(String name);
}
