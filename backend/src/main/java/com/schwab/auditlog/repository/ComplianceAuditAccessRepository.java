package com.schwab.auditlog.repository;

import com.schwab.auditlog.model.ComplianceAuditAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ComplianceAuditAccessRepository extends JpaRepository<ComplianceAuditAccess, Long> {
    @Query("select a from ComplianceAuditAccess a join AuditEvent e on e.id = a.auditEventId " +
           "where a.createdAt >= coalesce(:fromTime, a.createdAt) " +
           "and a.createdAt <= coalesce(:toTime, a.createdAt) " +
           "and (:actorId is null or e.actorId = :actorId) " +
           "and (:resourceId is null or e.resourceId = :resourceId) " +
           "and (:accessType is null or a.accessType = :accessType) " +
           "order by a.createdAt asc")
    List<ComplianceAuditAccess> findReportRecords(
        @Param("fromTime") LocalDateTime fromTime,
        @Param("toTime") LocalDateTime toTime,
        @Param("actorId") String actorId,
        @Param("resourceId") String resourceId,
        @Param("accessType") String accessType);
}
