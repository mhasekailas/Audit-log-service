package com.schwab.auditlog.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

/**
 * Audit Event Entity
 * 
 * Represents an immutable audit log record with cryptographic hash chain support.
 * Each record includes:
 * - contentHash: SHA-256 of the event fields
 * - chainHash: SHA-256 of (previous chainHash + contentHash)
 */
@Entity
@Table(name = "audit_events", indexes = {
    @Index(name = "idx_actor_id", columnList = "actor_id"),
    @Index(name = "idx_event_type", columnList = "event_type"),
    @Index(name = "idx_resource", columnList = "resource_type,resource_id"),
    @Index(name = "idx_timestamp", columnList = "timestamp"),
    @Index(name = "idx_sequence", columnList = "sequence_number"),
    @Index(name = "idx_is_archived", columnList = "is_archived")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;
    
    @Column(name = "actor_id", nullable = false, length = 255)
    private String actorId;
    
    @Column(name = "resource_type", nullable = false, length = 100)
    private String resourceType;
    
    @Column(name = "resource_id", nullable = false, length = 255)
    private String resourceId;
    
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode payload;
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
    
    @Column(name = "chain_hash", nullable = false, length = 64)
    private String chainHash;
    
    @Column(name = "sequence_number", nullable = false, unique = true)
    private Long sequenceNumber;

    // Client-supplied Idempotency-Key header value; enforces replay/duplicate-submit protection
    @Column(name = "idempotency_key", unique = true, length = 255)
    private String idempotencyKey;
    
    @Column(name = "is_archived", nullable = false)
    private Boolean isArchived = false;
    
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
