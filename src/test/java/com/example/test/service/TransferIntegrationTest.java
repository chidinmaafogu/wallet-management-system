package com.example.test.service;

import com.example.test.dto.CreateUserRequest;
import com.example.test.dto.FundAccountRequest;
import com.example.test.dto.TransferRequest;
import com.example.test.model.enums.TransactionStatus;
import com.example.test.support.WalletTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferIntegrationTest extends WalletTestSupport {

    @Test
    void registersAUserWithAGeneratedAccountAndZeroBalance() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType("application/json")
                        .content(json(new CreateUserRequest(
                                "Ada", "Okoro", "ada@example.com", "08031234567"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("ada@example.com"))
                .andExpect(jsonPath("$.data.accounts[0].accountNumber").isNotEmpty())
                .andExpect(jsonPath("$.data.accounts[0].status").value("ACTIVE"));
    }

    @Test
    void generatedAccountNumbersAreValidNubans() {
        String accountNumber = newAccount();
        assertThat(new NubanGenerator().isValid(properties.institutionCode(), accountNumber)).isTrue();
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "Ada", "Okoro", "duplicate@example.com", "08031111111");
        mockMvc.perform(post("/api/v1/users").contentType("application/json").content(json(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users")
                        .contentType("application/json")
                        .content(json(new CreateUserRequest(
                                "Bola", "Ade", "duplicate@example.com", "08032222222"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_USER"));
    }

    @Test
    @DisplayName("a transfer debits the source, credits the destination and writes a balanced pair of entries")
    void transferMovesMoneyAndWritesLedgerEntries() throws Exception {
        String source = newFundedAccount("2000.00");
        String destination = newAccount();

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType("application/json")
                        .header("Idempotency-Key", "transfer-happy-path")
                        .content(json(new TransferRequest(
                                source, destination, null, new BigDecimal("500.00"), "Rent"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.amount").value(500.00))
                .andExpect(jsonPath("$.data.reference").isNotEmpty())
                .andExpect(jsonPath("$.data.createdAt").value(org.hamcrest.Matchers.endsWith("+01:00")))
                .andExpect(jsonPath("$.data.completedAt").value(org.hamcrest.Matchers.endsWith("+01:00")));

        assertThat(balanceOf(source)).isEqualByComparingTo("1500.00");
        assertThat(balanceOf(destination)).isEqualByComparingTo("500.00");

        var transaction = walletTransactionRepo.findByIdempotencyKey("transfer-happy-path").orElseThrow();
        var entries = ledgerEntryRepo.findByTransactionId(transaction.getId());

        assertThat(entries).hasSize(2);
        assertThat(entries.stream().map(e -> e.signedAmount()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void declinesTransferWithInsufficientFundsAndRecordsTheReason() throws Exception {
        String source = newFundedAccount("100.00");
        String destination = newAccount();

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType("application/json")
                        .header("Idempotency-Key", "decline-insufficient")
                        .content(json(new TransferRequest(
                                source, destination, null, new BigDecimal("500.00"), "Too much"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertThat(balanceOf(source)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(destination)).isEqualByComparingTo("0.00");

        var declined = walletTransactionRepo.findByIdempotencyKey("decline-insufficient").orElseThrow();
        assertThat(declined.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(declined.getFailureReason()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(ledgerEntryRepo.findByTransactionId(declined.getId())).isEmpty();
    }

    @Test
    void rejectsTransferToTheSameAccount() throws Exception {
        String account = newFundedAccount("1000.00");

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType("application/json")
                        .header("Idempotency-Key", "same-account")
                        .content(json(new TransferRequest(
                                account, account, null, new BigDecimal("100.00"), "Self"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SAME_ACCOUNT_TRANSFER"));
    }

    @Test
    void rejectsUnknownDestinationAccount() throws Exception {
        String source = newFundedAccount("1000.00");

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType("application/json")
                        .header("Idempotency-Key", "unknown-destination")
                        .content(json(new TransferRequest(
                                source, "9999999999", null, new BigDecimal("100.00"), "Nowhere"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void refusesAForeignBankCodeInsteadOfTreatingItAsInternal() throws Exception {
        String source = newFundedAccount("1000.00");
        String destination = newAccount();

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType("application/json")
                        .header("Idempotency-Key", "foreign-bank")
                        .content(json(new TransferRequest(
                                source, destination, "000058", new BigDecimal("100.00"), "Other bank"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INTER_BANK_NOT_SUPPORTED"));

        assertThat(balanceOf(source)).isEqualByComparingTo("1000.00");
    }

    @Test
    void rejectsAmountsWithMoreThanTwoDecimalPlaces() throws Exception {
        String source = newFundedAccount("1000.00");
        String destination = newAccount();

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType("application/json")
                        .header("Idempotency-Key", "too-precise")
                        .content(json(new TransferRequest(
                                source, destination, null, new BigDecimal("10.005"), "Too precise"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.amount").isNotEmpty());
    }

    @Test
    void rejectsNonPositiveAmounts() throws Exception {
        String source = newFundedAccount("1000.00");
        String destination = newAccount();

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType("application/json")
                        .header("Idempotency-Key", "non-positive")
                        .content(json(new TransferRequest(
                                source, destination, null, new BigDecimal("0.00"), "Nothing"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void fundingBooksAgainstTheHouseAccountSoTheLedgerStaysBalanced() throws Exception {
        String account = newAccount();

        mockMvc.perform(post("/api/v1/accounts/" + account + "/fund")
                        .contentType("application/json")
                        .header("Idempotency-Key", "fund-house-account")
                        .content(json(new FundAccountRequest(new BigDecimal("10000.00"), "Deposit"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("FUNDING"));

        assertThat(balanceOf(account)).isEqualByComparingTo("10000.00");
        assertThat(balanceOf(properties.systemFundingAccount())).isEqualByComparingTo("-10000.00");
        assertThat(walletBalanceRepo.totalSystemBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void nameEnquiryReturnsTheAccountNameWithoutContactDetails() throws Exception {
        String account = newAccount();

        mockMvc.perform(get("/api/v1/accounts/" + account + "/name-enquiry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountName").isNotEmpty())
                .andExpect(jsonPath("$.data.accountNumber").value(account))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.phoneNumber").doesNotExist());
    }

    @Test
    void statementListsEntriesNewestFirst() throws Exception {
        String source = newFundedAccount("5000.00");
        String destination = newAccount();

        transactionService.transfer(new TransferRequest(
                source, destination, null, new BigDecimal("100.00"), "First"), null);
        transactionService.transfer(new TransferRequest(
                source, destination, null, new BigDecimal("200.00"), "Second"), null);

        mockMvc.perform(get("/api/v1/accounts/" + source + "/statement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries.length()").value(3))
                .andExpect(jsonPath("$.data.entries[0].narration").value("Second"))
                .andExpect(jsonPath("$.data.entries[0].direction").value("DEBIT"));
    }

    @Test
    void rejectsStatementSizeAboveTheMaximumWith400() throws Exception {
        String account = newFundedAccount("100.00");

        mockMvc.perform(get("/api/v1/accounts/" + account + "/statement").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsStatementSizeBelowTheMinimumWith400() throws Exception {
        String account = newFundedAccount("100.00");

        mockMvc.perform(get("/api/v1/accounts/" + account + "/statement").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void transactionCanBeRetrievedByReference() throws Exception {
        String source = newFundedAccount("1000.00");
        String destination = newAccount();

        var response = transactionService.transfer(new TransferRequest(
                source, destination, null, new BigDecimal("250.00"), "Lookup"), null);

        mockMvc.perform(get("/api/v1/transfers/" + response.reference()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(250.00))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }
}
