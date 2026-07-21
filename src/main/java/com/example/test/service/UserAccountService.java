package com.example.test.service;

import com.example.test.common.Money;
import com.example.test.config.CacheConfig;
import com.example.test.config.WalletProperties;
import com.example.test.dto.AccountResponse;
import com.example.test.dto.BalanceResponse;
import com.example.test.dto.CreateUserRequest;
import com.example.test.dto.NameEnquiryResponse;
import com.example.test.dto.StatementEntryResponse;
import com.example.test.dto.StatementResponse;
import com.example.test.dto.UserResponse;
import com.example.test.exception.WalletException;
import com.example.test.model.Account;
import com.example.test.model.LedgerEntry;
import com.example.test.model.User;
import com.example.test.model.WalletBalance;
import com.example.test.model.enums.AccountStatus;
import com.example.test.model.enums.AccountType;
import com.example.test.repo.AccountRepo;
import com.example.test.repo.LedgerEntryRepo;
import com.example.test.repo.UserRepo;
import com.example.test.repo.WalletBalanceRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountService {

    private final UserRepo userRepo;
    private final AccountRepo accountRepo;
    private final WalletBalanceRepo walletBalanceRepo;
    private final LedgerEntryRepo ledgerEntryRepo;
    private final NubanGenerator nubanGenerator;
    private final WalletProperties properties;

    @Transactional(rollbackFor = Exception.class)
    public UserResponse createUserAndAccount(CreateUserRequest request) {
        if (userRepo.existsByEmail(request.email())) {
            throw WalletException.duplicateUser("email");
        }
        if (userRepo.existsByPhoneNumber(request.phoneNumber())) {
            throw WalletException.duplicateUser("phone number");
        }

        User user = userRepo.save(User.builder()
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(request.email().trim().toLowerCase())
                .phoneNumber(request.phoneNumber().trim())
                .build());

        Account account = provisionAccount(user);

        log.info("Registered user id={} with account {}", user.getId(), account.getAccountNumber());
        return UserResponse.from(user, List.of(AccountResponse.from(account)));
    }

    @Transactional(rollbackFor = Exception.class)
    public Account provisionAccount(User user) {
        long serial = accountRepo.nextAccountSerial();
        String accountNumber = nubanGenerator.generate(properties.institutionCode(), serial);

        Account account = accountRepo.save(Account.builder()
                .institutionCode(properties.institutionCode())
                .accountNumber(accountNumber)
                .user(user)
                .accountType(AccountType.CUSTOMER)
                .currency(properties.defaultCurrency())
                .status(AccountStatus.ACTIVE)
                .build());

        walletBalanceRepo.save(WalletBalance.builder()
                .account(account)
                .balance(BigDecimal.ZERO)
                .build());

        return account;
    }

    @Transactional(readOnly = true)
    public UserResponse findUser(Long id) {
        User user = userRepo.findById(id).orElseThrow(() -> WalletException.userNotFound(id));
        List<AccountResponse> accounts = accountRepo.findByUserId(id).stream()
                .map(AccountResponse::from)
                .toList();
        return UserResponse.from(user, accounts);
    }

    @Transactional(readOnly = true)
    public Account requireAccount(String institutionCode, String accountNumber) {
        return accountRepo.findByInstitutionCodeAndAccountNumber(institutionCode, accountNumber)
                .orElseThrow(() -> WalletException.accountNotFound(accountNumber));
    }

    @Cacheable(cacheNames = CacheConfig.ACCOUNTS, key = "#accountNumber", sync = true)
    @Transactional(readOnly = true)
    public AccountResponse findAccount(String accountNumber) {
        return AccountResponse.from(requireAccount(properties.institutionCode(), accountNumber));
    }

    @Cacheable(cacheNames = CacheConfig.NAME_ENQUIRY,
            key = "#institutionCode + ':' + #accountNumber", sync = true)
    @Transactional(readOnly = true)
    public NameEnquiryResponse nameEnquiry(String institutionCode, String accountNumber) {
        Account account = requireAccount(institutionCode, accountNumber);
        String name = account.getUser() == null
                ? properties.institutionName()
                : account.getUser().getFullName();
        return new NameEnquiryResponse(
                account.getAccountNumber(), account.getInstitutionCode(), name, account.getStatus());
    }

    @Transactional(readOnly = true)
    public BalanceResponse findBalance(String accountNumber) {
        Account account = requireAccount(properties.institutionCode(), accountNumber);
        WalletBalance wallet = walletBalanceRepo.findByAccountId(account.getId())
                .orElseThrow(() -> WalletException.accountNotFound(accountNumber));
        return new BalanceResponse(
                account.getAccountNumber(),
                account.getCurrency(),
                Money.forApi(wallet.getBalance()),
                wallet.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public StatementResponse findStatement(String accountNumber, Long cursor, int size) {
        Account account = requireAccount(properties.institutionCode(), accountNumber);
        PageRequest limit = PageRequest.of(0, size + 1);

        List<LedgerEntry> entries = cursor == null
                ? ledgerEntryRepo.findByAccountIdOrderByIdDesc(account.getId(), limit)
                : ledgerEntryRepo.findByAccountIdAndIdLessThanOrderByIdDesc(account.getId(), cursor, limit);

        boolean hasMore = entries.size() > size;
        List<LedgerEntry> page = hasMore ? entries.subList(0, size) : entries;
        Long nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;

        return new StatementResponse(
                account.getAccountNumber(),
                page.stream().map(StatementEntryResponse::from).toList(),
                nextCursor,
                hasMore);
    }
}
