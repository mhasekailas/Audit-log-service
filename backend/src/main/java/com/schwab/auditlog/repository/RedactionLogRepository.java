package com.schwab.auditlog.repository;

import com.schwab.auditlog.model.RedactionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RedactionLogRepository extends JpaRepository<RedactionLog, Long> {
    List<RedactionLog> findByAuditEventIdOrderByRedactedAtAsc(Long auditEventId);
}
