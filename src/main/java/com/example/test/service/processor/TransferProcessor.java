package com.example.test.service.processor;

import com.example.test.config.WalletProperties;
import com.example.test.exception.WalletException;
import com.example.test.model.enums.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TransferProcessor implements TransactionProcessor {

    private final WalletProperties properties;

    @Override
    public TransactionType supports() {
        return TransactionType.TRANSFER;
    }

    @Override
    public void validate(TransactionContext context) {
        if (context.sourceAccount().getId().equals(context.destinationAccount().getId())) {
            throw WalletException.sameAccount();
        }
        if (context.sourceAccount().getCurrency() != context.destinationAccount().getCurrency()) {
            throw WalletException.currencyMismatch();
        }
        if (context.amount().compareTo(properties.maxTransferAmount()) > 0) {
            throw WalletException.limitExceeded(properties.maxTransferAmount());
        }
    }

    @Override
    public List<LedgerLeg> buildLegs(TransactionContext context) {
        return List.of(
                LedgerLeg.debit(context.sourceAccount(), context.amount(), context.narration()),
                LedgerLeg.credit(context.destinationAccount(), context.amount(), context.narration()));
    }
}
