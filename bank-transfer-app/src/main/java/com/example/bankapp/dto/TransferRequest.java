package com.example.bankapp.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Holds the data typed into the "Transfer Money" form. */
@Getter
@Setter
public class TransferRequest {

    // The account number sending the money
    @NotBlank(message = "Sender account is required")
    private String fromAccount;

    // The account number receiving the money
    @NotBlank(message = "Receiver account is required")
    private String toAccount;

    // The amount to transfer. Must be greater than 0 and have valid decimal places.
    @NotNull(message = "Transfer amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be strictly positive")
    @Digits(integer = 12, fraction = 2, message = "Invalid amount format")
    private BigDecimal amount;

    // Used to simulate a system crash mid-transfer to demonstrate @Transactional rollback
    private boolean simulateFailure;
}