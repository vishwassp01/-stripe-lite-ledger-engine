package com.vishwas.ledger.repository;

import com.vishwas.ledger.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    // Retrieve all journal entries associated with a specific account name.
    // Critical for retrieving detailed balance history and auditing transactions.
    List<LedgerEntry> findByAccountNameOrderByTransactionTimestampDesc(String accountName);
}
