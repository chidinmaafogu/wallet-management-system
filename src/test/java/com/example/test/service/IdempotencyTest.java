package com.example.test.service;

import com.example.test.dto.TransactionResponse;
import com.example.test.dto.TransferRequest;
import com.example.test.exception.ErrorCode;
import com.example.test.exception.WalletException;
import com.example.test.model.enums.TransactionStatus;
import com.example.test.support.WalletTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdempotencyTest extends WalletTestSupport {

    @Test
    @DisplayName("replaying a key returns the original receipt and does not debit twice")
    void replayReturnsOriginalReceiptWithoutMovingMoneyAgain() {
        String source = newFundedAccount("1000.00");
        String destination = newAccount();
        TransferRequest request = new TransferRequest(
                source, destination, null, new BigDecimal("300.00"), "Once only");

        TransactionResponse first = transactionService.transfer(request, "replay-key");
        TransactionResponse second = transactionService.transfer(request, "replay-key");

        assertThat(second.reference()).isEqualTo(first.reference());
        assertThat(balanceOf(source)).isEqualByComparingTo("700.00");
        assertThat(balanceOf(destination)).isEqualByComparingTo("300.00");
        assertThat(walletTransactionRepo.countByStatus(TransactionStatus.SUCCESS)).isEqualTo(2);
    }

    @Test
    @DisplayName("the same key with a different payload is rejected rather than silently replayed")
    void differentPayloadUnderTheSameKeyIsRejected() {
        String source = newFundedAccount("1000.00");
        String destination = newAccount();

        transactionService.transfer(new TransferRequest(
                source, destination, null, new BigDecimal("100.00"), "First"), "reused-key");

        assertThatThrownBy(() -> transactionService.transfer(new TransferRequest(
                source, destination, null, new BigDecimal("900.00"), "Different"), "reused-key"))
                .isInstanceOf(WalletException.class)
                .extracting(exception -> ((WalletException) exception).getErrorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSE);

        assertThat(balanceOf(source)).isEqualByComparingTo("900.00");
    }

    @Test
    @DisplayName("a declined transfer is itself idempotent - retrying returns the decline")
    void retryingADeclinedKeyReturnsTheDecline() {
        String source = newFundedAccount("50.00");
        String destination = newAccount();
        TransferRequest request = new TransferRequest(
                source, destination, null, new BigDecimal("500.00"), "Too much");

        assertThatThrownBy(() -> transactionService.transfer(request, "declined-key"))
                .isInstanceOf(WalletException.class);

        TransactionResponse replay = transactionService.transfer(request, "declined-key");

        assertThat(replay.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(replay.failureReason()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(balanceOf(source)).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("concurrent duplicates settle to one transfer, arbitrated by the unique index")
    void concurrentDuplicatesMoveMoneyOnlyOnce() throws Exception {
        String source = newFundedAccount("1000.00");
        String destination = newAccount();
        TransferRequest request = new TransferRequest(
                source, destination, null, new BigDecimal("400.00"), "Double click");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<TransactionResponse>> duplicates = List.of(
                () -> transactionService.transfer(request, "concurrent-key"),
                () -> transactionService.transfer(request, "concurrent-key"));

        List<Future<TransactionResponse>> futures = pool.invokeAll(duplicates);
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        long succeeded = futures.stream().filter(future -> {
            try {
                future.get();
                return true;
            } catch (Exception exception) {
                return false;
            }
        }).count();

        assertThat(succeeded).isGreaterThanOrEqualTo(1);
        assertThat(balanceOf(source)).isEqualByComparingTo("600.00");
        assertThat(balanceOf(destination)).isEqualByComparingTo("400.00");
        assertThat(walletTransactionRepo.countByStatus(TransactionStatus.SUCCESS)).isEqualTo(2);
    }

    @Test
    @DisplayName("an unknown outcome is resolvable by key - 404 proves it is safe to retry")
    void unknownOutcomeIsResolvableByKey() throws Exception {
        String source = newFundedAccount("1000.00");
        String destination = newAccount();

        mockMvc.perform(get("/api/v1/transfers/idempotency/never-used-key"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));

        transactionService.transfer(new TransferRequest(
                source, destination, null, new BigDecimal("120.00"), "Timed out"), "resolvable-key");

        mockMvc.perform(get("/api/v1/transfers/idempotency/resolvable-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.amount").value(120.00));
    }
}
