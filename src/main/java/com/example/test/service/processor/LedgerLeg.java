package com.example.test.service.processor;

import com.example.test.model.Account;
import com.example.test.model.enums.LedgerDirection;

import java.math.BigDecimal;

public record LedgerLeg(Account account, LedgerDirection direction, BigDecimal amount, String narration) {

    public static LedgerLeg debit(Account account, BigDecimal amount, String narration) {
        return new LedgerLeg(account, LedgerDirection.DEBIT, amount, narration);
    }

    public static LedgerLeg credit(Account account, BigDecimal amount, String narration) {
        return new LedgerLeg(account, LedgerDirection.CREDIT, amount, narration);
    }

    public BigDecimal signedAmount() {
        return direction == LedgerDirection.CREDIT ? amount : amount.negate();
    }
}
