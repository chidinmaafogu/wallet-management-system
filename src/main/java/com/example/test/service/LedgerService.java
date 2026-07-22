package com.example.test.service;

import com.example.test.exception.ErrorCode;
import com.example.test.exception.WalletException;
import com.example.test.model.Account;
import com.example.test.model.LedgerEntry;
import com.example.test.model.WalletBalance;
import com.example.test.model.WalletTransaction;
import com.example.test.model.enums.TransactionStatus;
import com.example.test.repo.LedgerEntryRepo;
import com.example.test.repo.WalletBalanceRepo;
import com.example.test.repo.WalletTransactionRepo;
import com.example.test.service.processor.LedgerLeg;
import com.example.test.service.processor.TransactionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final WalletBalanceRepo walletBalanceRepo;
    private final WalletTransactionRepo walletTransactionRepo;
    private final LedgerEntryRepo ledgerEntryRepo;
    private final Clock clock;

    @Transactional(rollbackFor = Exception.class)
    public WalletTransaction post(TransactionContext context, List<LedgerLeg> legs) {
        assertBalanced(legs);

        Map<Long, WalletBalance> wallets = lockWallets(legs);
        Map<Long, BigDecimal> projected = validateAndProject(legs, wallets);

        WalletTransaction transaction = walletTransactionRepo.save(WalletTransaction.builder()
                .reference(context.reference())
                .idempotencyKey(context.idempotencyKey())
                .requestHash(context.requestHash())
                .transactionType(context.type())
                .status(TransactionStatus.SUCCESS)
                .sourceAccount(managed(wallets, context.sourceAccount()))
                .destinationAccount(managed(wallets, context.destinationAccount()))
                .amount(context.amount())
                .currency(context.currency())
                .narration(context.narration())
                .completedAt(Instant.now(clock))
                .build());

        Map<Long, BigDecimal> running = new HashMap<>();
        for (LedgerLeg leg : legs) {
            Long accountId = leg.account().getId();
            WalletBalance wallet = wallets.get(accountId);
            BigDecimal before = running.getOrDefault(accountId, wallet.getBalance());
            BigDecimal after = before.add(leg.signedAmount());
            running.put(accountId, after);

            ledgerEntryRepo.save(LedgerEntry.builder()
                    .transaction(transaction)
                    .account(wallet.getAccount())
                    .direction(leg.direction())
                    .amount(leg.amount())
                    .balanceBefore(before)
                    .balanceAfter(after)
                    .narration(leg.narration())
                    .build());
        }

        projected.forEach((accountId, balance) -> wallets.get(accountId).setBalance(balance));

        log.info("Posted {} {} reference={} amount={}",
                context.type(), context.currency(), context.reference(), context.amount());

        return transaction;
    }

    private void assertBalanced(List<LedgerLeg> legs) {
        if (legs == null || legs.size() < 2) {
            throw WalletException.unbalancedLedger();
        }
        BigDecimal net = legs.stream()
                .map(LedgerLeg::signedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (net.compareTo(BigDecimal.ZERO) != 0) {
            throw WalletException.unbalancedLedger();
        }
    }

    private Map<Long, WalletBalance> lockWallets(List<LedgerLeg> legs) {
        List<Long> accountIds = legs.stream()
                .map(leg -> leg.account().getId())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();

        Map<Long, WalletBalance> locked = new LinkedHashMap<>();
        for (Long accountId : accountIds) {
            locked.put(accountId, walletBalanceRepo.lockByAccountId(accountId)
                    .orElseThrow(() -> new WalletException(ErrorCode.ACCOUNT_NOT_FOUND,
                            "No wallet exists for account id " + accountId)));
        }
        return locked;
    }

    private Map<Long, BigDecimal> validateAndProject(List<LedgerLeg> legs, Map<Long, WalletBalance> wallets) {
        Map<Long, BigDecimal> projected = new HashMap<>();
        Set<Long> statusChecked = new HashSet<>();

        for (LedgerLeg leg : legs) {
            Long accountId = leg.account().getId();
            WalletBalance wallet = wallets.get(accountId);
            Account account = wallet.getAccount();

            if (statusChecked.add(accountId) && !account.isActive()) {
                throw WalletException.accountNotActive(account.getAccountNumber());
            }

            BigDecimal current = projected.getOrDefault(accountId, wallet.getBalance());
            BigDecimal next = current.add(leg.signedAmount());

            if (next.signum() < 0 && !account.isSystemAccount()) {
                throw WalletException.insufficientFunds(account.getAccountNumber());
            }
            projected.put(accountId, next);
        }
        return projected;
    }

    private Account managed(Map<Long, WalletBalance> wallets, Account account) {
        WalletBalance wallet = wallets.get(account.getId());
        return wallet == null ? account : wallet.getAccount();
    }
}
