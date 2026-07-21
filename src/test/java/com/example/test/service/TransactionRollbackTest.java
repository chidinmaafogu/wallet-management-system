package com.example.test.service;

import com.example.test.dto.TransferRequest;
import com.example.test.model.Account;
import com.example.test.model.enums.Currency;
import com.example.test.model.enums.TransactionStatus;
import com.example.test.model.enums.TransactionType;
import com.example.test.service.processor.LedgerLeg;
import com.example.test.service.processor.TransactionContext;
import com.example.test.support.WalletTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionRollbackTest extends WalletTestSupport {

    @Autowired
    private LedgerService ledgerService;

    @Test
    @DisplayName("a failure while writing the ledger reverts the debit, the credit and the transaction row")
    void failureWhileWritingTheLedgerRevertsEverything() {
        String source = newFundedAccount("2000.00");
        String destination = newFundedAccount("500.00");

        BigDecimal sourceBefore = balanceOf(source);
        BigDecimal destinationBefore = balanceOf(destination);
        long entriesBefore = ledgerEntryRepo.count();
        long successesBefore = walletTransactionRepo.countByStatus(TransactionStatus.SUCCESS);

        String oversizedNarration = "x".repeat(300);

        assertThatThrownBy(() -> transactionService.transfer(new TransferRequest(
                source, destination, null, new BigDecimal("750.00"), oversizedNarration), "rollback-key"))
                .isInstanceOf(RuntimeException.class);

        assertThat(balanceOf(source)).isEqualByComparingTo(sourceBefore);
        assertThat(balanceOf(destination)).isEqualByComparingTo(destinationBefore);
        assertThat(ledgerEntryRepo.count()).isEqualTo(entriesBefore);
        assertThat(walletTransactionRepo.countByStatus(TransactionStatus.SUCCESS)).isEqualTo(successesBefore);
        assertThat(walletTransactionRepo.findByIdempotencyKey("rollback-key")).isEmpty();
    }

    @Test
    @DisplayName("unbalanced legs are refused before anything is written")
    void unbalancedLegsAreRefusedAndWriteNothing() {
        String source = newFundedAccount("1000.00");
        String destination = newFundedAccount("1000.00");

        Account sourceAccount = userAccountService.requireAccount(properties.institutionCode(), source);
        Account destinationAccount = userAccountService
                .requireAccount(properties.institutionCode(), destination);

        long entriesBefore = ledgerEntryRepo.count();
        long transactionsBefore = walletTransactionRepo.count();

        TransactionContext context = new TransactionContext(
                TransactionType.TRANSFER, sourceAccount, destinationAccount,
                new BigDecimal("100.00"), Currency.NGN, "Unbalanced",
                "TRF-BROKEN-1", "unbalanced-key", "hash");

        List<LedgerLeg> unbalanced = List.of(
                LedgerLeg.debit(sourceAccount, new BigDecimal("100.00"), "Out"),
                LedgerLeg.credit(destinationAccount, new BigDecimal("90.00"), "In"));

        assertThatThrownBy(() -> ledgerService.post(context, unbalanced))
                .hasMessageContaining("balance");

        assertThat(ledgerEntryRepo.count()).isEqualTo(entriesBefore);
        assertThat(walletTransactionRepo.count()).isEqualTo(transactionsBefore);
        assertThat(balanceOf(source)).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(destination)).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("a decline leaves the balances untouched and writes no ledger entries")
    void declineWritesNoLedgerEntries() {
        String source = newFundedAccount("100.00");
        String destination = newFundedAccount("100.00");
        long entriesBefore = ledgerEntryRepo.count();

        assertThatThrownBy(() -> transactionService.transfer(new TransferRequest(
                source, destination, null, new BigDecimal("5000.00"), "Too much"), "decline-key"))
                .hasMessageContaining("Insufficient funds");

        assertThat(balanceOf(source)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(destination)).isEqualByComparingTo("100.00");
        assertThat(ledgerEntryRepo.count()).isEqualTo(entriesBefore);

        var declined = walletTransactionRepo.findByIdempotencyKey("decline-key").orElseThrow();
        assertThat(declined.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(declined.getFailureReason()).isEqualTo("INSUFFICIENT_FUNDS");
    }
}
