package com.example.test.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FundAccountRequest(

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 15, fraction = 2, message = "Amount must have at most 2 decimal places")
        @Schema(example = "10000.00")
        BigDecimal amount,

        @Size(max = 255, message = "Narration must not exceed 255 characters")
        @Schema(example = "Opening deposit")
        String narration
) {
}
