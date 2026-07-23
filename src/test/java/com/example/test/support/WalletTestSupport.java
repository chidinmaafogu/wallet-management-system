package com.example.test.support;

import com.example.test.config.WalletProperties;
import com.example.test.dto.CreateUserRequest;
import com.example.test.dto.FundAccountRequest;
import com.example.test.dto.UserResponse;
import com.example.test.repo.AccountRepo;
import com.example.test.repo.LedgerEntryRepo;
import com.example.test.repo.UserRepo;
import com.example.test.repo.WalletBalanceRepo;
import com.example.test.repo.WalletTransactionRepo;
import com.example.test.service.TransactionService;
import com.example.test.service.UserAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "DB_POOL_SIZE=5",
        "DB_CONNECTION_TIMEOUT_MS=10000",
        "SERVER_PORT=0",
        "WALLET_INSTITUTION_CODE=000999",
        "WALLET_INSTITUTION_NAME=3Line Wallet",
        "WALLET_DEFAULT_CURRENCY=NGN",
        "WALLET_SYSTEM_FUNDING_ACCOUNT=0000000000",
        "WALLET_MIN_TRANSFER_AMOUNT=0.01",
        "WALLET_MAX_TRANSFER_AMOUNT=1000000.00",
        "WALLET_LOCK_TIMEOUT_MS=5000",
        "WALLET_CACHE_TTL_SECONDS=300",
        "WALLET_SEED_DEMO_DATA=false",
        "WALLET_TIMEZONE=Africa/Lagos",
        "CACHE_TYPE=none",
        "REDIS_HOST=localhost",
        "REDIS_PORT=6379",
        "REDIS_PASSWORD=",
        "REDIS_TIMEOUT_MS=2000",
        "REDIS_POOL_MAX_ACTIVE=8",
        "REDIS_POOL_MAX_IDLE=4",
        "REDIS_POOL_MIN_IDLE=0"
})
public abstract class WalletTestSupport {

    private static final AtomicInteger UNIQUE = new AtomicInteger(1000);

    @DynamicPropertySource
    static void testDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnv("TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> requiredEnv("TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> requiredEnv("TEST_DB_PASSWORD"));
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    name + " is not set. The test suite runs against a real PostgreSQL database "
                            + "and truncates its tables, so it must point at a throwaway database. "
                            + "See the \"Running the tests\" section of README.md.");
        }
        return value;
    }

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected UserAccountService userAccountService;
    @Autowired
    protected TransactionService transactionService;
    @Autowired
    protected UserRepo userRepo;
    @Autowired
    protected AccountRepo accountRepo;
    @Autowired
    protected WalletBalanceRepo walletBalanceRepo;
    @Autowired
    protected WalletTransactionRepo walletTransactionRepo;
    @Autowired
    protected LedgerEntryRepo ledgerEntryRepo;
    @Autowired
    protected WalletProperties properties;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected CacheManager cacheManager;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("DELETE FROM ledger_entries");
        jdbcTemplate.execute("DELETE FROM wallet_transactions");
        jdbcTemplate.execute("DELETE FROM wallet_balances WHERE account_id IN "
                + "(SELECT id FROM accounts WHERE account_type = 'CUSTOMER')");
        jdbcTemplate.execute("DELETE FROM accounts WHERE account_type = 'CUSTOMER'");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("UPDATE wallet_balances SET balance = 0");
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    protected String newAccount() {
        int unique = UNIQUE.incrementAndGet();
        UserResponse user = userAccountService.createUserAndAccount(new CreateUserRequest(
                "Test", "User" + unique,
                "user" + unique + "@example.com",
                "080" + String.format("%08d", unique)));
        return user.accounts().get(0).accountNumber();
    }

    protected String newFundedAccount(String amount) {
        String accountNumber = newAccount();
        transactionService.fund(accountNumber,
                new FundAccountRequest(new BigDecimal(amount), "Test funding"), null);
        return accountNumber;
    }

    protected BigDecimal balanceOf(String accountNumber) {
        return userAccountService.findBalance(accountNumber).balance();
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
