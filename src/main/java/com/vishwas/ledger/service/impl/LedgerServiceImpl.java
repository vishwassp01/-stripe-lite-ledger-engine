package com.vishwas.ledger.service.impl;

import com.vishwas.ledger.dto.AccountCreateRequest;
import com.vishwas.ledger.dto.AccountResponse;
import com.vishwas.ledger.dto.TransferRequest;
import com.vishwas.ledger.dto.TransferResponse;
import com.vishwas.ledger.entity.Account;
import com.vishwas.ledger.entity.LedgerEntry;
import com.vishwas.ledger.repository.AccountRepository;
import com.vishwas.ledger.repository.LedgerEntryRepository;
import com.vishwas.ledger.service.LedgerService;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LedgerServiceImpl implements LedgerService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerTransactionHelper ledgerTransactionHelper;

    public LedgerServiceImpl(AccountRepository accountRepository,
                             LedgerEntryRepository ledgerEntryRepository,
                             LedgerTransactionHelper ledgerTransactionHelper) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.ledgerTransactionHelper = ledgerTransactionHelper;
    }

    @Override
    @Transactional
    public AccountResponse createAccount(AccountCreateRequest request) {
        if (accountRepository.findByName(request.getName()).isPresent()) {
            throw new IllegalArgumentException("An account with name [" + request.getName() + "] already exists");
        }

        Account account = Account.builder()
                .name(request.getName())
                .balance(request.getInitialBalance())
                .build();

        account = accountRepository.save(account);

        return AccountResponse.builder()
                .id(account.getId())
                .name(account.getName())
                .balance(account.getBalance())
                .build();
    }

    @Override
    public TransferResponse transferFunds(TransferRequest request, String referenceId) {
        int maxRetries = 20;
        int attempt = 0;

        while (true) {
            try {
                // Delegate to helper inside an isolated transactional boundary
                return ledgerTransactionHelper.executeTransfer(request, referenceId);
            } catch (ObjectOptimisticLockingFailureException e) {
                attempt++;
                if (attempt >= maxRetries) {
                    // Maximum retry threshold reached. Propagate locking failure.
                    throw e;
                }
                
                // Concurrency collision detected. Wait brief randomized duration before retrying.
                // Linear/Exponential randomized backoff is an industry best practice for load distribution.
                try {
                    Thread.sleep(50 + (long) (Math.random() * 100));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccount(String name) {
        Account account = accountRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("Account not found: " + name));

        return AccountResponse.builder()
                .id(account.getId())
                .name(account.getName())
                .balance(account.getBalance())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerEntry> getAccountHistory(String name) {
        // Assert that the account actually exists first
        if (accountRepository.findByName(name).isEmpty()) {
            throw new RuntimeException("Account not found: " + name);
        }
        return ledgerEntryRepository.findByAccountNameOrderByTransactionTimestampDesc(name);
    }
}
