package com.vishwas.ledger.repository;

import com.vishwas.ledger.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByReferenceId(String referenceId);

    boolean existsByReferenceId(String referenceId);
}
