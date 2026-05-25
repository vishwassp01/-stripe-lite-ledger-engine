package com.vishwas.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.Builder
public class TransferResponse {
    private String referenceId;
    private String sourceAccountName;
    private String destinationAccountName;
    private BigDecimal amount;
    private String status;
    private LocalDateTime timestamp;
}
