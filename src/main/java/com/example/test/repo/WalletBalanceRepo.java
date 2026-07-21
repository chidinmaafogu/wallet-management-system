package com.example.test.repo;

import com.example.test.model.WalletBalance;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface WalletBalanceRepo extends JpaRepository<WalletBalance, Long> {

    Optional<WalletBalance> findByAccountId(Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select w from WalletBalance w where w.account.id = :accountId")
    Optional<WalletBalance> lockByAccountId(@Param("accountId") Long accountId);

    @Query("select coalesce(sum(w.balance), 0) from WalletBalance w")
    BigDecimal totalSystemBalance();
}
