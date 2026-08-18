package com.schwab.auditlog.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Singleton row used purely as a lock target (id is always 1).
 * SELECT ... FOR UPDATE on this row serializes chain-tail writers at the database level,
 * so the guarantee holds even across multiple application instances sharing one database -
 * unlike a JVM-local lock, which only protects a single process.
 */
@Entity
@Table(name = "chain_lock")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChainLock {
    @Id
    private Long id;
}
