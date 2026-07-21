package com.example.test.dto;

import com.example.test.model.Account;
import com.example.test.model.enums.AccountStatus;
import com.example.test.model.enums.AccountType;
import com.example.test.model.enums.Currency;

import java.time.LocalDateTime;

public record AccountResponse(
        String accountNumber,
        String institutionCode,
        String accountName,
        AccountType accountType,
        Currency currency,
        AccountStatus status,
        LocalDateTime createdAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getAccountNumber(),
                account.getInstitutionCode(),
                account.getUser() == null ? null : account.getUser().getFullName(),
                account.getAccountType(),
                account.getCurrency(),
                account.getStatus(),
                account.getCreatedAt());
    }
}
