package com.example.test.service.processor;

import com.example.test.model.Account;
import com.example.test.model.enums.Currency;
import com.example.test.model.enums.TransactionType;

import java.math.BigDecimal;

public record TransactionContext(
        TransactionType type,
        Account sourceAccount,
        Account destinationAccount,
        BigDecimal amount,
        Currency currency,
        String narration,
        String reference,
        String idempotencyKey,
        String requestHash
) {
}
