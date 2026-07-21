package com.example.test.controller;

import com.example.test.common.ApiResponse;
import com.example.test.config.WalletProperties;
import com.example.test.dto.AccountResponse;
import com.example.test.dto.BalanceResponse;
import com.example.test.dto.FundAccountRequest;
import com.example.test.dto.NameEnquiryResponse;
import com.example.test.dto.StatementResponse;
import com.example.test.dto.TransactionResponse;
import com.example.test.service.TransactionService;
import com.example.test.service.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Validated
@Tag(name = "Accounts", description = "Account lookup, balance, statement and funding")
public class AccountController {

    private final UserAccountService userAccountService;
    private final TransactionService transactionService;
    private final WalletProperties properties;

    @GetMapping("/{accountNumber}")
    @Operation(summary = "Fetch account details")
    public ResponseEntity<ApiResponse<AccountResponse>> find(@PathVariable String accountNumber) {
        return ResponseEntity.ok(
                ApiResponse.ok(userAccountService.findAccount(accountNumber), "Account retrieved"));
    }

    @GetMapping("/{accountNumber}/name-enquiry")
    @Operation(summary = "Resolve an account holder's name",
            description = "Returns only the account name and status. Contact details are never exposed here.")
    public ResponseEntity<ApiResponse<NameEnquiryResponse>> nameEnquiry(
            @PathVariable String accountNumber,
            @Parameter(description = "Institution code. Defaults to this institution.")
            @RequestParam(required = false) String bankCode) {
        String institutionCode = bankCode == null || bankCode.isBlank()
                ? properties.institutionCode()
                : bankCode;
        return ResponseEntity.ok(ApiResponse.ok(
                userAccountService.nameEnquiry(institutionCode, accountNumber), "Account resolved"));
    }

    @GetMapping("/{accountNumber}/balance")
    @Operation(summary = "Fetch the current wallet balance")
    public ResponseEntity<ApiResponse<BalanceResponse>> balance(@PathVariable String accountNumber) {
        return ResponseEntity.ok(
                ApiResponse.ok(userAccountService.findBalance(accountNumber), "Balance retrieved"));
    }

    @GetMapping("/{accountNumber}/statement")
    @Operation(summary = "Fetch the ledger statement",
            description = "Keyset paginated. Pass the returned nextCursor to fetch the following page.")
    public ResponseEntity<ApiResponse<StatementResponse>> statement(
            @PathVariable String accountNumber,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                userAccountService.findStatement(accountNumber, cursor, size), "Statement retrieved"));
    }

    @PostMapping("/{accountNumber}/fund")
    @Operation(summary = "Fund an account",
            description = "Simulates an inbound credit. Booked as a double-entry movement from the house account.")
    public ResponseEntity<ApiResponse<TransactionResponse>> fund(
            @PathVariable String accountNumber,
            @Valid @RequestBody FundAccountRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        TransactionResponse response = transactionService.fund(accountNumber, request, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.ok(response, "Account funded"));
    }
}
