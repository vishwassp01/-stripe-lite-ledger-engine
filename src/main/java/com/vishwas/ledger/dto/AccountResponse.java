package com.vishwas.ledger.dto;

import java.math.BigDecimal;

@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.Builder
public class AccountResponse {
    private Long id;
    private String name;
    private BigDecimal balance;
}
