package com.example.test.dto;

import com.example.test.common.Money;
import com.example.test.model.WalletTransaction;
import com.example.test.model.enums.Currency;
import com.example.test.model.enums.TransactionStatus;
import com.example.test.model.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        String reference,
        TransactionType type,
        TransactionStatus status,
        String sourceAccountNumber,
        String destinationAccountNumber,
        BigDecimal amount,
        Currency currency,
        String narration,
        String failureReason,
        BigDecimal sourceBalanceAfter,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
    public static TransactionResponse from(WalletTransaction transaction, BigDecimal sourceBalanceAfter) {
        return new TransactionResponse(
                transaction.getReference(),
                transaction.getTransactionType(),
                transaction.getStatus(),
                transaction.getSourceAccount().getAccountNumber(),
                transaction.getDestinationAccount().getAccountNumber(),
                Money.forApi(transaction.getAmount()),
                transaction.getCurrency(),
                transaction.getNarration(),
                transaction.getFailureReason(),
                sourceBalanceAfter,
                transaction.getCreatedAt(),
                transaction.getCompletedAt());
    }
}
