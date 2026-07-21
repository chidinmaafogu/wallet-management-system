package com.example.test.repo;

import com.example.test.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AccountRepo extends JpaRepository<Account, Long> {

    Optional<Account> findByInstitutionCodeAndAccountNumber(String institutionCode, String accountNumber);

    boolean existsByInstitutionCodeAndAccountNumber(String institutionCode, String accountNumber);

    List<Account> findByUserId(Long userId);

    @Query(value = "SELECT nextval('nuban_serial_seq')", nativeQuery = true)
    Long nextAccountSerial();
}
