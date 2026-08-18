package com.schwab.auditlog.repository;

import com.schwab.auditlog.model.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for AuditEvent entity
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    
    /**
     * Find events by actor ID with pagination
     */
    Page<AuditEvent> findByActorIdAndIsArchivedFalse(String actorId, Pageable pageable);
    
    /**
     * Find events by event type with pagination
     */
    Page<AuditEvent> findByEventTypeAndIsArchivedFalse(String eventType, Pageable pageable);
    
    /**
     * Find events by resource type and resource ID
     */
    Page<AuditEvent> findByResourceTypeAndResourceIdAndIsArchivedFalse(
        String resourceType, String resourceId, Pageable pageable);
    
    /**
     * Find events within a time range
     */
    Page<AuditEvent> findByTimestampBetweenAndIsArchivedFalse(
        LocalDateTime from, LocalDateTime to, Pageable pageable);
    
    /**
     * Find all non-archived events ordered by sequence
     */
    List<AuditEvent> findByIsArchivedFalseOrderBySequenceNumberAsc();

    List<AuditEvent> findAllByOrderBySequenceNumberAsc();

    List<AuditEvent> findByActorIdAndIsArchivedFalseOrderBySequenceNumberAsc(String actorId);

    List<AuditEvent> findByResourceIdAndIsArchivedFalseOrderBySequenceNumberAsc(String resourceId);

    List<AuditEvent> findByActorIdOrderBySequenceNumberAsc(String actorId);

    List<AuditEvent> findByResourceIdOrderBySequenceNumberAsc(String resourceId);

    List<AuditEvent> findByTimestampBeforeAndIsArchivedFalse(LocalDateTime cutoff);
    
    /**
     * Find the last record to get the latest chain hash
     */
    @Query("SELECT e FROM AuditEvent e WHERE e.isArchived = false ORDER BY e.sequenceNumber DESC LIMIT 1")
    Optional<AuditEvent> findLastRecord();

    /**
     * Find an existing event by its idempotency key (replay/duplicate-submit detection)
     */
    Optional<AuditEvent> findByIdempotencyKey(String idempotencyKey);
    
    /**
     * Find events with multiple filter criteria
     */
    @Query("SELECT e FROM AuditEvent e WHERE " +
           "(:actorId IS NULL OR e.actorId = :actorId) AND " +
           "(:eventType IS NULL OR e.eventType = :eventType) AND " +
           "(:resourceType IS NULL OR e.resourceType = :resourceType) AND " +
           "(:resourceId IS NULL OR e.resourceId = :resourceId) AND " +
           "e.timestamp >= COALESCE(:fromTime, e.timestamp) AND " +
           "e.timestamp <= COALESCE(:toTime, e.timestamp) AND " +
           "e.isArchived = false " +
           "ORDER BY e.sequenceNumber DESC")
    Page<AuditEvent> findByMultipleCriteria(
        @Param("actorId") String actorId,
        @Param("eventType") String eventType,
        @Param("resourceType") String resourceType,
        @Param("resourceId") String resourceId,
        @Param("fromTime") LocalDateTime fromTime,
        @Param("toTime") LocalDateTime toTime,
        Pageable pageable
    );
    
    /**
     * Get the next sequence number
     */
    @Query(value = "SELECT COALESCE(MAX(sequence_number), 0) + 1 FROM audit_events", nativeQuery = true)
    Long getNextSequenceNumber();
    
    /**
     * Count non-archived events
     */
    Long countByIsArchivedFalse();
}
