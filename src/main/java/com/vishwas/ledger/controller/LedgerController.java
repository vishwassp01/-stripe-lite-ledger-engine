package com.vishwas.ledger.controller;

import com.vishwas.ledger.dto.AccountCreateRequest;
import com.vishwas.ledger.dto.AccountResponse;
import com.vishwas.ledger.dto.TransferRequest;
import com.vishwas.ledger.dto.TransferResponse;
import com.vishwas.ledger.entity.LedgerEntry;
import com.vishwas.ledger.service.LedgerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    // Opens a new account (or customer wallet) with an initial opening balance.
    @PostMapping("/accounts")
    public ResponseEntity<AccountResponse> createAccount(@RequestBody @Valid AccountCreateRequest request) {
        AccountResponse response = ledgerService.createAccount(request);
        return ResponseEntity.ok(response);
    }

    // Processes a double-entry fund transfer between two accounts.
    // The "Idempotency-Key" header is passed as the unique transaction Reference ID
    // for seamless audit matching and reconciliation.
    @PostMapping("/transfer")
    public ResponseEntity<TransferResponse> transferFunds(
            @RequestBody @Valid TransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        TransferResponse response = ledgerService.transferFunds(request, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    // Fetches the current metadata and balance of a given account.
    @GetMapping("/accounts/{name}/balance")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable String name) {
        AccountResponse response = ledgerService.getAccount(name);
        return ResponseEntity.ok(response);
    }

    // Fetches the chronological audit history (debits and credits) of a given account.
    @GetMapping("/accounts/{name}/history")
    public ResponseEntity<List<LedgerEntry>> getAccountHistory(@PathVariable String name) {
        List<LedgerEntry> response = ledgerService.getAccountHistory(name);
        return ResponseEntity.ok(response);
    }
}
