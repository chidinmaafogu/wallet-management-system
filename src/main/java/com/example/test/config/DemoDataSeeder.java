package com.example.test.config;

import com.example.test.dto.CreateUserRequest;
import com.example.test.dto.FundAccountRequest;
import com.example.test.dto.UserResponse;
import com.example.test.repo.UserRepo;
import com.example.test.service.TransactionService;
import com.example.test.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "wallet.seed-demo-data", havingValue = "true")
public class DemoDataSeeder {

    private final UserAccountService userAccountService;
    private final TransactionService transactionService;
    private final UserRepo userRepo;

    @Bean
    public ApplicationRunner seedDemoData() {
        return args -> {
            if (userRepo.count() > 0) {
                return;
            }

            UserResponse ada = userAccountService.createUserAndAccount(new CreateUserRequest(
                    "Ada", "Okoro", "ada@example.com", "08031234567"));
            UserResponse bola = userAccountService.createUserAndAccount(new CreateUserRequest(
                    "Bola", "Adeyemi", "bola@example.com", "08069876543"));

            String adaAccount = ada.accounts().get(0).accountNumber();
            String bolaAccount = bola.accounts().get(0).accountNumber();

            transactionService.fund(adaAccount,
                    new FundAccountRequest(new BigDecimal("100000.00"), "Opening deposit"), null);
            transactionService.fund(bolaAccount,
                    new FundAccountRequest(new BigDecimal("50000.00"), "Opening deposit"), null);

            log.info("Demo data ready: Ada={} (100000.00), Bola={} (50000.00)", adaAccount, bolaAccount);
        };
    }
}
