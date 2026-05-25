package com.vishwas.ledger.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class TransferRequest {

    @NotBlank(message = "Source account name is required")
    private String sourceAccountName;

    @NotBlank(message = "Destination account name is required")
    private String destinationAccountName;

    @NotNull(message = "Transfer amount is required")
    @DecimalMin(value = "0.0001", message = "Transfer amount must be positive and greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Transfer description is required")
    private String description;
}
