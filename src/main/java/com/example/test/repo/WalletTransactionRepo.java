package com.example.test.repo;

import com.example.test.model.WalletTransaction;
import com.example.test.model.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletTransactionRepo extends JpaRepository<WalletTransaction, Long> {

    Optional<WalletTransaction> findByReference(String reference);

    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);

    @Query("select t from WalletTransaction t "
            + "join fetch t.sourceAccount join fetch t.destinationAccount "
            + "where t.reference = :reference")
    Optional<WalletTransaction> findByReferenceWithAccounts(@Param("reference") String reference);

    @Query("select t from WalletTransaction t "
            + "join fetch t.sourceAccount join fetch t.destinationAccount "
            + "where t.idempotencyKey = :idempotencyKey")
    Optional<WalletTransaction> findByIdempotencyKeyWithAccounts(
            @Param("idempotencyKey") String idempotencyKey);

    long countByStatus(TransactionStatus status);
}
