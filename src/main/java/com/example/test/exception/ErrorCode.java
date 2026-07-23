package com.example.test.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, false),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, false),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, false),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, false),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, false),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, false),
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, false),
    DUPLICATE_USER(HttpStatus.CONFLICT, false),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY, true),
    ACCOUNT_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, true),
    SAME_ACCOUNT_TRANSFER(HttpStatus.UNPROCESSABLE_ENTITY, false),
    CURRENCY_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, false),
    INTER_BANK_NOT_SUPPORTED(HttpStatus.UNPROCESSABLE_ENTITY, false),
    TRANSFER_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, true),
    IDEMPOTENCY_KEY_REUSE(HttpStatus.CONFLICT, false),
    UNBALANCED_LEDGER(HttpStatus.INTERNAL_SERVER_ERROR, false),
    WALLET_LOCKED(HttpStatus.SERVICE_UNAVAILABLE, false),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, false);

    private final HttpStatus status;
    private final boolean recordable;

    ErrorCode(HttpStatus status, boolean recordable) {
        this.status = status;
        this.recordable = recordable;
    }

    public HttpStatus status() {
        return status;
    }

    public boolean isRecordable() {
        return recordable;
    }
}
