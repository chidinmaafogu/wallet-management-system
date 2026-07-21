package com.example.test.dto;

import com.example.test.model.enums.Currency;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BalanceResponse(
        String accountNumber,
        Currency currency,
        BigDecimal balance,
        LocalDateTime asOf
) {
}
