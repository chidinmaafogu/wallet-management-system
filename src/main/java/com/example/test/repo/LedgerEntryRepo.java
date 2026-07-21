package com.example.test.repo;

import com.example.test.model.LedgerEntry;
import com.example.test.model.enums.LedgerDirection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface LedgerEntryRepo extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByAccountIdOrderByIdDesc(Long accountId, Pageable pageable);

    List<LedgerEntry> findByAccountIdAndIdLessThanOrderByIdDesc(Long accountId, Long cursor, Pageable pageable);

    List<LedgerEntry> findByTransactionId(Long transactionId);

    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.direction = :direction")
    BigDecimal sumByDirection(@Param("direction") LedgerDirection direction);

    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e "
            + "where e.account.id = :accountId and e.direction = :direction")
    BigDecimal sumByAccountAndDirection(@Param("accountId") Long accountId,
                                        @Param("direction") LedgerDirection direction);
}
