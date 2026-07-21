package com.example.test.service.processor;

import com.example.test.model.enums.TransactionType;

import java.util.List;

public interface TransactionProcessor {

    TransactionType supports();

    void validate(TransactionContext context);

    List<LedgerLeg> buildLegs(TransactionContext context);
}
