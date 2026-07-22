package com.example.test.dto;

import com.example.test.common.Money;
import com.example.test.model.LedgerEntry;
import com.example.test.model.enums.LedgerDirection;

import java.math.BigDecimal;
import java.time.Instant;

public record StatementEntryResponse(
        Long entryId,
        String reference,
        LedgerDirection direction,
        BigDecimal amount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        String narration,
        Instant createdAt
) {
    public static StatementEntryResponse from(LedgerEntry entry) {
        return new StatementEntryResponse(
                entry.getId(),
                entry.getTransaction().getReference(),
                entry.getDirection(),
                Money.forApi(entry.getAmount()),
                Money.forApi(entry.getBalanceBefore()),
                Money.forApi(entry.getBalanceAfter()),
                entry.getNarration(),
                entry.getCreatedAt());
    }
}
