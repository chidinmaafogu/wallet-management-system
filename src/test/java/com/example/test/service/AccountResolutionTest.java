package com.example.test.service;

import com.example.test.exception.WalletException;
import com.example.test.model.Account;
import com.example.test.support.WalletTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountResolutionTest extends WalletTestSupport {

    private static final String FOREIGN_INSTITUTION = "000058";

    @Test
    @DisplayName("the same NUBAN under two institution codes is two different accounts")
    void sameAccountNumberUnderDifferentInstitutionsResolvesSeparately() {
        String accountNumber = newAccount();
        Account ours = userAccountService.requireAccount(properties.institutionCode(), accountNumber);

        jdbcTemplate.update(
                "INSERT INTO accounts (institution_code, account_number, user_id, account_type, "
                        + "currency, status, created_at) VALUES (?, ?, NULL, 'CUSTOMER', 'NGN', 'ACTIVE', "
                        + "CURRENT_TIMESTAMP)",
                FOREIGN_INSTITUTION, accountNumber);

        Account theirs = userAccountService.requireAccount(FOREIGN_INSTITUTION, accountNumber);

        assertThat(ours.getAccountNumber()).isEqualTo(theirs.getAccountNumber());
        assertThat(ours.getId()).isNotEqualTo(theirs.getId());
        assertThat(ours.getInstitutionCode()).isEqualTo(properties.institutionCode());
        assertThat(theirs.getInstitutionCode()).isEqualTo(FOREIGN_INSTITUTION);
    }

    @Test
    @DisplayName("an account number alone does not identify an account")
    void anAccountNumberIsNotResolvableWithoutAnInstitutionCode() {
        String accountNumber = newAccount();

        assertThatThrownBy(() -> userAccountService.requireAccount("000123", accountNumber))
                .isInstanceOf(WalletException.class);
    }

    @Test
    void nameEnquiryResolvesAccountsWithinTheGivenInstitution() {
        String accountNumber = newAccount();

        var enquiry = userAccountService.nameEnquiry(properties.institutionCode(), accountNumber);

        assertThat(enquiry.accountNumber()).isEqualTo(accountNumber);
        assertThat(enquiry.institutionCode()).isEqualTo(properties.institutionCode());
        assertThat(enquiry.accountName()).isNotBlank();
    }

    @Test
    void houseAccountIsSeededAndResolvable() {
        Account house = userAccountService.requireAccount(
                properties.institutionCode(), properties.systemFundingAccount());

        assertThat(house.isSystemAccount()).isTrue();
        assertThat(house.getUser()).isNull();
    }
}
