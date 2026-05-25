package com.vishwas.ledger.service.impl;

import com.vishwas.ledger.dto.TransferRequest;
import com.vishwas.ledger.dto.TransferResponse;
import com.vishwas.ledger.entity.Account;
import com.vishwas.ledger.entity.LedgerEntry;
import com.vishwas.ledger.entity.Transaction;
import com.vishwas.ledger.exception.OverdraftException;
import com.vishwas.ledger.repository.AccountRepository;
import com.vishwas.ledger.repository.LedgerEntryRepository;
import com.vishwas.ledger.repository.TransactionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class LedgerTransactionHelper {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerTransactionHelper(AccountRepository accountRepository,
                                   TransactionRepository transactionRepository,
                                   LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    // Propagation.REQUIRES_NEW ensures that each retry runs in a completely fresh, isolated transaction.
    // If a concurrent transaction modified the balances and throws an Optimistic Lock exception,
    // this transaction rolls back cleanly, and the parent retry loop can start a brand new one.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransferResponse executeTransfer(TransferRequest request, String referenceId) {

        // 1. Load accounts from the database
        Account sourceAccount = accountRepository.findByName(request.getSourceAccountName())
                .orElseThrow(() -> new RuntimeException("Source account not found: " + request.getSourceAccountName()));

        Account destinationAccount = accountRepository.findByName(request.getDestinationAccountName())
                .orElseThrow(() -> new RuntimeException("Destination account not found: " + request.getDestinationAccountName()));

        // 2. Prevent self-transfers (a standard FinTech bookkeeping requirement)
        if (sourceAccount.getName().equals(destinationAccount.getName())) {
            throw new IllegalArgumentException("Cannot transfer funds to the same account");
        }

        // 3. Validate balance (Overdraft protection)
        if (sourceAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new OverdraftException(sourceAccount.getName(), "Insufficient funds for transfer");
        }

        // 4. Update balances in memory (Hibernate dirty checking handles database updates)
        sourceAccount.setBalance(sourceAccount.getBalance().subtract(request.getAmount()));
        destinationAccount.setBalance(destinationAccount.getBalance().add(request.getAmount()));

        // 5. Save Parent Transaction Journal Header
        Transaction transaction = Transaction.builder()
                .referenceId(referenceId)
                .description(request.getDescription())
                .timestamp(LocalDateTime.now())
                .build();
        transaction = transactionRepository.save(transaction);

        // 6. Save Double-Entry Ledger Lines
        // Line A: Debit the source account (Subtract funds)
        LedgerEntry debitEntry = LedgerEntry.builder()
                .transaction(transaction)
                .account(sourceAccount)
                .type(LedgerEntry.EntryType.DEBIT)
                .amount(request.getAmount())
                .build();
        ledgerEntryRepository.save(debitEntry);

        // Line B: Credit the destination account (Add funds)
        LedgerEntry creditEntry = LedgerEntry.builder()
                .transaction(transaction)
                .account(destinationAccount)
                .type(LedgerEntry.EntryType.CREDIT)
                .amount(request.getAmount())
                .build();
        ledgerEntryRepository.save(creditEntry);

        // Explicitly flush repositories to force version checks right away
        // This makes sure OptimisticLockingFailureExceptions are thrown inside the transactional boundary!
        accountRepository.saveAndFlush(sourceAccount);
        accountRepository.saveAndFlush(destinationAccount);

        return TransferResponse.builder()
                .referenceId(referenceId)
                .sourceAccountName(sourceAccount.getName())
                .destinationAccountName(destinationAccount.getName())
                .amount(request.getAmount())
                .status("SUCCESS")
                .timestamp(transaction.getTimestamp())
                .build();
    }
}
