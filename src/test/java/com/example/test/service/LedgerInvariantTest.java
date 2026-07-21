package com.example.test.service;

import com.example.test.dto.TransferRequest;
import com.example.test.model.Account;
import com.example.test.model.enums.LedgerDirection;
import com.example.test.support.WalletTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerInvariantTest extends WalletTestSupport {

    @Test
    @DisplayName("every debit is matched by a credit, so the books always sum to zero")
    void ledgerSumsToZero() {
        String ada = newFundedAccount("100000.00");
        String bola = newFundedAccount("50000.00");
        String chidi = newAccount();

        transactionService.transfer(new TransferRequest(
                ada, bola, null, new BigDecimal("2500.00"), "One"), null);
        transactionService.transfer(new TransferRequest(
                bola, chidi, null, new BigDecimal("1250.50"), "Two"), null);
        transactionService.transfer(new TransferRequest(
                chidi, ada, null, new BigDecimal("100.25"), "Three"), null);

        assertThat(ledgerEntryRepo.sumByDirection(LedgerDirection.DEBIT))
                .isEqualByComparingTo(ledgerEntryRepo.sumByDirection(LedgerDirection.CREDIT));
    }

    @Test
    @DisplayName("the cached balance always equals the ledger it is derived from")
    void cachedBalanceMatchesTheLedgerForEveryAccount() {
        String ada = newFundedAccount("100000.00");
        String bola = newFundedAccount("50000.00");
        String chidi = newAccount();

        transactionService.transfer(new TransferRequest(
                ada, bola, null, new BigDecimal("2500.00"), "One"), null);
        transactionService.transfer(new TransferRequest(
                bola, chidi, null, new BigDecimal("1250.50"), "Two"), null);
        transactionService.transfer(new TransferRequest(
                ada, chidi, null, new BigDecimal("999.99"), "Three"), null);

        for (Account account : accountRepo.findAll()) {
            BigDecimal credits = ledgerEntryRepo
                    .sumByAccountAndDirection(account.getId(), LedgerDirection.CREDIT);
            BigDecimal debits = ledgerEntryRepo
                    .sumByAccountAndDirection(account.getId(), LedgerDirection.DEBIT);
            BigDecimal cached = walletBalanceRepo.findByAccountId(account.getId())
                    .orElseThrow().getBalance();

            assertThat(cached)
                    .as("cached balance for account %s", account.getAccountNumber())
                    .isEqualByComparingTo(credits.subtract(debits));
        }
    }

    @Test
    @DisplayName("total money across every account is always zero, because funding has a counterparty")
    void systemWideBalanceIsAlwaysZero() {
        String ada = newFundedAccount("100000.00");
        String bola = newFundedAccount("50000.00");

        transactionService.transfer(new TransferRequest(
                ada, bola, null, new BigDecimal("7500.00"), "Move"), null);

        assertThat(walletBalanceRepo.totalSystemBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balanceOf(properties.systemFundingAccount())).isEqualByComparingTo("-150000.00");
    }

    @Test
    @DisplayName("each ledger entry records the balance before and after it was applied")
    void everyEntryCarriesItsRunningBalance() {
        String source = newFundedAccount("1000.00");
        String destination = newAccount();

        var response = transactionService.transfer(new TransferRequest(
                source, destination, null, new BigDecimal("400.00"), "Audit"), null);
        var transaction = walletTransactionRepo.findByReference(response.reference()).orElseThrow();

        var entries = ledgerEntryRepo.findByTransactionId(transaction.getId());
        assertThat(entries).hasSize(2);

        for (var entry : entries) {
            assertThat(entry.getBalanceAfter())
                    .isEqualByComparingTo(entry.getBalanceBefore().add(entry.signedAmount()));
        }
    }
}
