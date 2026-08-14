package com.schwab.auditlog.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "redaction_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RedactionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "audit_event_id", nullable = false)
    private Long auditEventId;

    @Column(name = "field_path", nullable = false, length = 255)
    private String fieldPath;

    @Column(name = "redacted_at", nullable = false)
    private LocalDateTime redactedAt;

    @Column(name = "redaction_reason", length = 500)
    private String redactionReason;

    @Column(name = "redaction_hash", nullable = false, length = 64)
    private String redactionHash;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (redactedAt == null) redactedAt = LocalDateTime.now();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
