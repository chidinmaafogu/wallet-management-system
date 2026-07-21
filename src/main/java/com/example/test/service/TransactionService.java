package com.example.test.service;

import com.example.test.dto.FundAccountRequest;
import com.example.test.dto.TransactionResponse;
import com.example.test.dto.TransferRequest;

public interface TransactionService {

    TransactionResponse transfer(TransferRequest request, String idempotencyKey);

    TransactionResponse fund(String accountNumber, FundAccountRequest request, String idempotencyKey);

    TransactionResponse findByReference(String reference);

    TransactionResponse findByIdempotencyKey(String idempotencyKey);
}
