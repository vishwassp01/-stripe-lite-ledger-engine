package com.vishwas.ledger.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class OverdraftException extends RuntimeException {
    public OverdraftException(String accountName, String message) {
        super(String.format("Account [%s] transfer rejected: %s", accountName, message));
    }
}
