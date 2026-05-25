package com.vishwas.ledger.service;

import com.vishwas.ledger.dto.AccountCreateRequest;
import com.vishwas.ledger.dto.AccountResponse;
import com.vishwas.ledger.dto.TransferRequest;
import com.vishwas.ledger.dto.TransferResponse;
import com.vishwas.ledger.entity.LedgerEntry;

import java.util.List;

public interface LedgerService {

    AccountResponse createAccount(AccountCreateRequest request);

    TransferResponse transferFunds(TransferRequest request, String referenceId);

    AccountResponse getAccount(String name);

    List<LedgerEntry> getAccountHistory(String name);
}
