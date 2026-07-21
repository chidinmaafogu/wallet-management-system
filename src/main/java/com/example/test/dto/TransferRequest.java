package com.example.test.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransferRequest(

        @NotBlank(message = "Source account number is required")
        @Pattern(regexp = "\\d{10}", message = "Account number must be exactly 10 digits")
        @Schema(example = "1000000012")
        String sourceAccountNumber,

        @NotBlank(message = "Destination account number is required")
        @Pattern(regexp = "\\d{10}", message = "Account number must be exactly 10 digits")
        @Schema(example = "1000000023")
        String destinationAccountNumber,

        @Pattern(regexp = "\\d{3,6}", message = "Bank code must be 3 to 6 digits")
        @Schema(description = "Destination institution code. Omit for a transfer within this institution.",
                example = "000999")
        String destinationBankCode,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 15, fraction = 2, message = "Amount must have at most 2 decimal places")
        @Schema(example = "500.00")
        BigDecimal amount,

        @Size(max = 255, message = "Narration must not exceed 255 characters")
        @Schema(example = "Rent contribution")
        String narration
) {
}
