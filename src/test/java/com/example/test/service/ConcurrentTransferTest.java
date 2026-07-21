package com.example.test.service;

import com.example.test.dto.TransferRequest;
import com.example.test.model.enums.LedgerDirection;
import com.example.test.support.WalletTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentTransferTest extends WalletTestSupport {

    private static final int TRANSFERS = 200;
    private static final int THREADS = 16;
    private static final BigDecimal OPENING_BALANCE = new BigDecimal("100000.00");

    @Test
    @DisplayName("200 simultaneous bidirectional transfers conserve every kobo and never deadlock")
    void concurrentTransfersConserveMoney() throws Exception {
        String accountA = newFundedAccount(OPENING_BALANCE.toPlainString());
        String accountB = newFundedAccount(OPENING_BALANCE.toPlainString());
        BigDecimal openingTotal = balanceOf(accountA).add(balanceOf(accountB));

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startLine = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(TRANSFERS);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();

        for (int i = 0; i < TRANSFERS; i++) {
            boolean aToB = i % 2 == 0;
            BigDecimal amount = new BigDecimal(10 + (i % 90)).setScale(2);
            pool.submit(() -> {
                try {
                    startLine.await();
                    transactionService.transfer(new TransferRequest(
                            aToB ? accountA : accountB,
                            aToB ? accountB : accountA,
                            null, amount, "Concurrent"), null);
                    succeeded.incrementAndGet();
                } catch (Throwable throwable) {
                    failed.incrementAndGet();
                    firstFailure.compareAndSet(null, throwable);
                } finally {
                    finished.countDown();
                }
            });
        }

        startLine.countDown();
        assertThat(finished.await(120, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        if (firstFailure.get() != null) {
            throw new AssertionError(
                    failed.get() + " of " + TRANSFERS + " transfers failed", firstFailure.get());
        }

        assertThat(succeeded.get()).isEqualTo(TRANSFERS);
        assertThat(balanceOf(accountA).add(balanceOf(accountB))).isEqualByComparingTo(openingTotal);
        assertThat(balanceOf(accountA)).isGreaterThan(BigDecimal.ZERO);
        assertThat(balanceOf(accountB)).isGreaterThan(BigDecimal.ZERO);

        assertThat(ledgerEntryRepo.sumByDirection(LedgerDirection.DEBIT))
                .isEqualByComparingTo(ledgerEntryRepo.sumByDirection(LedgerDirection.CREDIT));
    }

    @Test
    @DisplayName("draining a wallet concurrently never overdraws it")
    void concurrentWithdrawalsNeverOverdrawTheWallet() throws Exception {
        String source = newFundedAccount("1000.00");
        String destination = newAccount();

        int attempts = 40;
        BigDecimal amount = new BigDecimal("100.00");

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startLine = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(attempts);
        List<Throwable> failures = new ArrayList<>();
        AtomicInteger succeeded = new AtomicInteger();

        for (int i = 0; i < attempts; i++) {
            pool.submit(() -> {
                try {
                    startLine.await();
                    transactionService.transfer(new TransferRequest(
                            source, destination, null, amount, "Drain"), null);
                    succeeded.incrementAndGet();
                } catch (Throwable throwable) {
                    synchronized (failures) {
                        failures.add(throwable);
                    }
                } finally {
                    finished.countDown();
                }
            });
        }

        startLine.countDown();
        assertThat(finished.await(120, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(succeeded.get()).isEqualTo(10);
        assertThat(balanceOf(source)).isEqualByComparingTo("0.00");
        assertThat(balanceOf(destination)).isEqualByComparingTo("1000.00");
        assertThat(failures).hasSize(attempts - 10);
    }
}
