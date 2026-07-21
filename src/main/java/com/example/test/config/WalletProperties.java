package com.example.test.config;

import com.example.test.model.enums.Currency;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "wallet")
public record WalletProperties(
        String institutionCode,
        String institutionName,
        Currency defaultCurrency,
        String systemFundingAccount,
        BigDecimal minTransferAmount,
        BigDecimal maxTransferAmount,
        long lockTimeoutMillis,
        long cacheTtlSeconds,
        boolean seedDemoData
) {
    public boolean isOwnInstitution(String bankCode) {
        return bankCode == null || bankCode.isBlank() || institutionCode.equals(bankCode);
    }
}
