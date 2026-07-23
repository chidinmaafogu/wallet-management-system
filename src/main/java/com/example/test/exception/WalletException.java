package com.example.test.exception;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class WalletException extends RuntimeException {

    private final ErrorCode errorCode;

    public WalletException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static WalletException accountNotFound(String accountNumber) {
        return new WalletException(ErrorCode.ACCOUNT_NOT_FOUND,
                "Account %s was not found".formatted(accountNumber));
    }

    public static WalletException userNotFound(Long id) {
        return new WalletException(ErrorCode.USER_NOT_FOUND, "User %d was not found".formatted(id));
    }

    public static WalletException transactionNotFound(String reference) {
        return new WalletException(ErrorCode.TRANSACTION_NOT_FOUND,
                "No transaction found for reference %s".formatted(reference));
    }

    public static WalletException duplicateUser(String field) {
        return new WalletException(ErrorCode.DUPLICATE_USER,
                "A user with that %s is already registered".formatted(field));
    }

    public static WalletException insufficientFunds(String accountNumber) {
        return new WalletException(ErrorCode.INSUFFICIENT_FUNDS,
                "Insufficient funds in account %s".formatted(accountNumber));
    }

    public static WalletException accountNotActive(String accountNumber) {
        return new WalletException(ErrorCode.ACCOUNT_NOT_ACTIVE,
                "Account %s is not active".formatted(accountNumber));
    }

    public static WalletException sameAccount() {
        return new WalletException(ErrorCode.SAME_ACCOUNT_TRANSFER,
                "Source and destination accounts must be different");
    }

    public static WalletException currencyMismatch() {
        return new WalletException(ErrorCode.CURRENCY_MISMATCH,
                "Source and destination accounts hold different currencies");
    }

    public static WalletException interBankNotSupported(String bankCode) {
        return new WalletException(ErrorCode.INTER_BANK_NOT_SUPPORTED,
                "Bank code %s is not this institution; inter-bank transfer is not supported".formatted(bankCode));
    }

    public static WalletException limitExceeded(BigDecimal max) {
        return new WalletException(ErrorCode.TRANSFER_LIMIT_EXCEEDED,
                "Amount exceeds the maximum permitted transfer of %s".formatted(max.toPlainString()));
    }

    public static WalletException idempotencyKeyReuse(String key) {
        return new WalletException(ErrorCode.IDEMPOTENCY_KEY_REUSE,
                "Idempotency key %s was already used with a different request payload".formatted(key));
    }

    public static WalletException idempotencyKeyRequired() {
        return new WalletException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                "An Idempotency-Key header is required for money-moving requests");
    }

    public static WalletException unbalancedLedger() {
        return new WalletException(ErrorCode.UNBALANCED_LEDGER,
                "Ledger entries do not balance to zero");
    }

    public static WalletException walletLocked() {
        return new WalletException(ErrorCode.WALLET_LOCKED,
                "The account is busy with another transaction, please retry");
    }
}
