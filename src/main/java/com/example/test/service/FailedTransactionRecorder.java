package com.example.test.service;

import com.example.test.exception.WalletException;
import com.example.test.model.WalletTransaction;
import com.example.test.model.enums.TransactionStatus;
import com.example.test.repo.WalletTransactionRepo;
import com.example.test.service.processor.TransactionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class FailedTransactionRecorder {

    private final WalletTransactionRepo walletTransactionRepo;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(TransactionContext context, WalletException failure) {
        try {
            walletTransactionRepo.save(WalletTransaction.builder()
                    .reference(context.reference())
                    .idempotencyKey(context.idempotencyKey())
                    .requestHash(context.requestHash())
                    .transactionType(context.type())
                    .status(TransactionStatus.FAILED)
                    .sourceAccount(context.sourceAccount())
                    .destinationAccount(context.destinationAccount())
                    .amount(context.amount())
                    .currency(context.currency())
                    .narration(context.narration())
                    .failureReason(failure.getErrorCode().name())
                    .completedAt(LocalDateTime.now())
                    .build());
            log.warn("Recorded declined {} reference={} reason={}",
                    context.type(), context.reference(), failure.getErrorCode());
        } catch (RuntimeException exception) {
            log.error("Could not persist declined transaction reference={}", context.reference(), exception);
        }
    }
}
