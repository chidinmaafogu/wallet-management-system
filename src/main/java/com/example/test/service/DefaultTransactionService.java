package com.example.test.service;

import com.example.test.common.Money;
import com.example.test.config.WalletProperties;
import com.example.test.dto.FundAccountRequest;
import com.example.test.dto.TransactionResponse;
import com.example.test.dto.TransferRequest;
import com.example.test.exception.ErrorCode;
import com.example.test.exception.WalletException;
import com.example.test.model.Account;
import com.example.test.model.WalletTransaction;
import com.example.test.model.enums.TransactionType;
import com.example.test.repo.WalletBalanceRepo;
import com.example.test.repo.WalletTransactionRepo;
import com.example.test.service.processor.TransactionContext;
import com.example.test.service.processor.TransactionProcessor;
import com.example.test.service.processor.TransactionProcessorFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultTransactionService implements TransactionService {

    private static final DateTimeFormatter REFERENCE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final UserAccountService userAccountService;
    private final LedgerService ledgerService;
    private final FailedTransactionRecorder failedTransactionRecorder;
    private final TransactionProcessorFactory processorFactory;
    private final WalletTransactionRepo walletTransactionRepo;
    private final WalletBalanceRepo walletBalanceRepo;
    private final WalletProperties properties;

    @Override
    public TransactionResponse transfer(TransferRequest request, String idempotencyKey) {
        if (!properties.isOwnInstitution(request.destinationBankCode())) {
            throw WalletException.interBankNotSupported(request.destinationBankCode());
        }

        String requestHash = hash("TRANSFER", request.sourceAccountNumber(),
                request.destinationAccountNumber(), request.amount().toPlainString());

        Optional<TransactionResponse> replay = replayIfPresent(idempotencyKey, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        Account source = userAccountService.requireAccount(
                properties.institutionCode(), request.sourceAccountNumber());
        Account destination = userAccountService.requireAccount(
                properties.institutionCode(), request.destinationAccountNumber());

        TransactionContext context = new TransactionContext(
                TransactionType.TRANSFER,
                source,
                destination,
                Money.forApi(request.amount()),
                source.getCurrency(),
                request.narration(),
                reference("TRF"),
                resolveKey(idempotencyKey),
                requestHash);

        return execute(context);
    }

    @Override
    public TransactionResponse fund(String accountNumber, FundAccountRequest request, String idempotencyKey) {
        String requestHash = hash("FUNDING", accountNumber, request.amount().toPlainString());

        Optional<TransactionResponse> replay = replayIfPresent(idempotencyKey, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        Account house = userAccountService.requireAccount(
                properties.institutionCode(), properties.systemFundingAccount());
        Account destination = userAccountService.requireAccount(
                properties.institutionCode(), accountNumber);

        TransactionContext context = new TransactionContext(
                TransactionType.FUNDING,
                house,
                destination,
                Money.forApi(request.amount()),
                destination.getCurrency(),
                request.narration() == null ? "Account funding" : request.narration(),
                reference("FND"),
                resolveKey(idempotencyKey),
                requestHash);

        return execute(context);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse findByReference(String reference) {
        WalletTransaction transaction = walletTransactionRepo.findByReferenceWithAccounts(reference)
                .orElseThrow(() -> WalletException.transactionNotFound(reference));
        return toResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse findByIdempotencyKey(String idempotencyKey) {
        WalletTransaction transaction = walletTransactionRepo.findByIdempotencyKeyWithAccounts(idempotencyKey)
                .orElseThrow(() -> WalletException.transactionNotFound(idempotencyKey));
        return toResponse(transaction);
    }

    private TransactionResponse execute(TransactionContext context) {
        TransactionProcessor processor = processorFactory.forType(context.type());
        try {
            processor.validate(context);
            WalletTransaction transaction = ledgerService.post(context, processor.buildLegs(context));
            return toResponse(transaction);
        } catch (DataIntegrityViolationException exception) {
            return walletTransactionRepo.findByIdempotencyKeyWithAccounts(context.idempotencyKey())
                    .map(this::toResponse)
                    .orElseThrow(() -> exception);
        } catch (WalletException exception) {
            if (exception.getErrorCode().isRecordable()) {
                failedTransactionRecorder.record(context, exception);
            }
            throw exception;
        }
    }

    private Optional<TransactionResponse> replayIfPresent(String idempotencyKey, String requestHash) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return walletTransactionRepo.findByIdempotencyKeyWithAccounts(idempotencyKey)
                .map(existing -> {
                    if (existing.getRequestHash() != null && !existing.getRequestHash().equals(requestHash)) {
                        throw WalletException.idempotencyKeyReuse(idempotencyKey);
                    }
                    log.info("Replaying idempotent request key={} reference={}",
                            idempotencyKey, existing.getReference());
                    return toResponse(existing);
                });
    }

    private TransactionResponse toResponse(WalletTransaction transaction) {
        BigDecimal sourceBalance = walletBalanceRepo
                .findByAccountId(transaction.getSourceAccount().getId())
                .map(wallet -> Money.forApi(wallet.getBalance()))
                .orElse(null);
        return TransactionResponse.from(transaction, sourceBalance);
    }

    private String resolveKey(String idempotencyKey) {
        return idempotencyKey == null || idempotencyKey.isBlank()
                ? UUID.randomUUID().toString()
                : idempotencyKey;
    }

    private String reference(String prefix) {
        return "%s-%s-%s".formatted(
                prefix,
                LocalDate.now().format(REFERENCE_DATE),
                UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    private String hash(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(String.join("|", parts).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new WalletException(ErrorCode.INTERNAL_ERROR, "SHA-256 is unavailable");
        }
    }
}
