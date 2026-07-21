package com.example.test.controller;

import com.example.test.common.ApiResponse;
import com.example.test.dto.TransactionResponse;
import com.example.test.dto.TransferRequest;
import com.example.test.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
@Tag(name = "Transfers", description = "Fund transfer between accounts")
public class TransferController {

    private final TransactionService transactionService;

    @PostMapping
    @Operation(summary = "Transfer funds between two accounts",
            description = "Send an Idempotency-Key header to make retries safe. "
                    + "Replaying a key returns the original outcome instead of moving money again.")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        TransactionResponse response = transactionService.transfer(request, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.ok(response, "Transfer successful"));
    }

    @GetMapping("/{reference}")
    @Operation(summary = "Fetch a transaction by reference")
    public ResponseEntity<ApiResponse<TransactionResponse>> byReference(@PathVariable String reference) {
        return ResponseEntity.ok(
                ApiResponse.ok(transactionService.findByReference(reference), "Transaction retrieved"));
    }

    @GetMapping("/idempotency/{key}")
    @Operation(summary = "Resolve a transfer whose outcome is unknown",
            description = "For a client that timed out. A 200 returns the outcome and the request must not "
                    + "be retried. A 404 means the transfer did not happen and is safe to retry.")
    public ResponseEntity<ApiResponse<TransactionResponse>> byIdempotencyKey(@PathVariable String key) {
        return ResponseEntity.ok(
                ApiResponse.ok(transactionService.findByIdempotencyKey(key), "Transaction retrieved"));
    }
}
