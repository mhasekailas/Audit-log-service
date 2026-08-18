package com.schwab.auditlog.repository;

import com.schwab.auditlog.model.ChainLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChainLockRepository extends JpaRepository<ChainLock, Long> {

    /**
     * Acquires a database-level exclusive row lock (SELECT ... FOR UPDATE) that is held for the
     * remainder of the current transaction. Concurrent transactions calling this block until the
     * lock holder commits/rolls back, serializing chain-tail sequence/hash generation across every
     * application instance connected to this database.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ChainLock c WHERE c.id = 1")
    Optional<ChainLock> lockChainTailForUpdate();
}
