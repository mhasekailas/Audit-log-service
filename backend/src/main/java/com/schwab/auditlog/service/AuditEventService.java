package com.schwab.auditlog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.schwab.auditlog.dto.AuditEventResponse;
import com.schwab.auditlog.dto.ChainVerificationResponse;
import com.schwab.auditlog.dto.CreateEventRequest;
import com.schwab.auditlog.model.AuditEvent;
import com.schwab.auditlog.repository.AuditEventRepository;
import com.schwab.auditlog.repository.ChainLockRepository;
import com.schwab.auditlog.util.HashUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Service class for audit event operations
 * Handles write, query, and verification of audit events
 */
@Service
@Slf4j
public class AuditEventService {
    
    private final AuditEventRepository eventRepository;
    private final ChainLockRepository chainLockRepository;
    private final HashUtil hashUtil;

    // Serializes sequence-number/chain-hash generation within this JVM; the DB row lock
    // acquired in createEvent() is what makes the guarantee hold across multiple instances.
    private final Object chainWriteLock = new Object();
    
    public AuditEventService(AuditEventRepository eventRepository, ChainLockRepository chainLockRepository,
                              HashUtil hashUtil) {
        this.eventRepository = eventRepository;
        this.chainLockRepository = chainLockRepository;
        this.hashUtil = hashUtil;
    }

    /**
     * Returns true if an event was already recorded for the given Idempotency-Key.
     */
    @Transactional(readOnly = true)
    public boolean isDuplicateIdempotencyKey(String idempotencyKey) {
        return idempotencyKey != null && !idempotencyKey.isBlank()
            && eventRepository.findByIdempotencyKey(idempotencyKey).isPresent();
    }

    /**
     * Returns true when the authenticated principal has admin authority for audit management.
     */
    public boolean isAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
            .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_AUDIT_ADMIN"));
    }

    /**
     * Enforce that a writer can only create events for their own actor identity unless they are admin.
     */
    public void assertActorIdentityMatchesPrincipal(Authentication authentication, String actorId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        if (isAdmin(authentication)) {
            return;
        }
        String principalName = authentication.getName();
        if (actorId == null || !principalName.equals(actorId)) {
            throw new AccessDeniedException("Access denied for the requested actor identity");
        }
    }

    /**
     * Enforce that a user can only view records belonging to their own actor identity unless they are admin.
     */
    public void assertEventVisibleToPrincipal(Authentication authentication, AuditEventResponse event) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        if (isAdmin(authentication)) {
            return;
        }
        String principalName = authentication.getName();
        if (event == null || event.getActorId() == null || !principalName.equals(event.getActorId())) {
            throw new AccessDeniedException("Access denied for the requested actor identity");
        }
    }

    /**
     * Enforce that a user can only view records belonging to their own actor identity unless they are admin.
     */
    public void assertEventVisibleToPrincipal(Authentication authentication, AuditEvent event) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        if (isAdmin(authentication)) {
            return;
        }
        String principalName = authentication.getName();
        if (event == null || event.getActorId() == null || !principalName.equals(event.getActorId())) {
            throw new AccessDeniedException("Access denied for the requested actor identity");
        }
    }

    /**
     * Create and store a new audit event with hash chain
     */
    @Transactional
    public AuditEventResponse createEvent(CreateEventRequest request) {
        return createEvent(request, null);
    }

    /**
     * Create and store a new audit event with hash chain.
     * If idempotencyKey matches an existing event, the original event is returned instead of
     * creating a duplicate (replay protection for retried/duplicate client submissions).
     */
    @Transactional
    public AuditEventResponse createEvent(CreateEventRequest request, String idempotencyKey) {
        log.debug("Creating event: {} for actor: {}", request.getEventType(), request.getActorId());

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<AuditEvent> existing = eventRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent replay detected for key: {}", idempotencyKey);
                return mapToResponse(existing.get());
            }
        }

        // Serialize sequence-number/chain-hash generation so concurrent writers cannot race on the chain tail
        synchronized (chainWriteLock) {
            // Database-level lock: blocks until any other transaction (this instance or another
            // app instance sharing the DB) holding it commits/rolls back, so the chain tail is
            // read-then-written atomically cluster-wide, not just within this JVM.
            chainLockRepository.lockChainTailForUpdate()
                .orElseThrow(() -> new IllegalStateException("Chain lock row is missing; refusing unlocked audit write"));

            // Use server-assigned timestamp if not provided
            LocalDateTime timestamp = request.getTimestamp() != null ? 
                request.getTimestamp() : LocalDateTime.now();
            timestamp = timestamp.truncatedTo(ChronoUnit.MICROS);
            
            // Get next sequence number
            Long sequenceNumber = eventRepository.getNextSequenceNumber();
            
            // Compute content hash
            String contentHash = hashUtil.computeContentHash(
                request.getEventType(),
                request.getActorId(),
                request.getResourceType(),
                request.getResourceId(),
                request.getPayload(),
                timestamp.toString()
            );
            
            // Get previous chain hash or use genesis hash
            String previousChainHash;
            Optional<AuditEvent> lastEvent = eventRepository.findLastRecord();
            if (lastEvent.isPresent()) {
                previousChainHash = lastEvent.get().getChainHash();
            } else {
                previousChainHash = hashUtil.getGenesisHash();
            }
            
            // Compute chain hash
            String chainHash = hashUtil.computeChainHash(previousChainHash, contentHash);
            
            // Create and save the event
            AuditEvent event = AuditEvent.builder()
                .eventType(request.getEventType())
                .actorId(request.getActorId())
                .resourceType(request.getResourceType())
                .resourceId(request.getResourceId())
                .payload(request.getPayload())
                .timestamp(timestamp)
                .contentHash(contentHash)
                .chainHash(chainHash)
                .sequenceNumber(sequenceNumber)
                .idempotencyKey(idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : null)
                .isArchived(false)
                .build();

            AuditEvent savedEvent;
            try {
                savedEvent = eventRepository.save(event);
                eventRepository.flush();
            } catch (DataIntegrityViolationException e) {
                // Duplicate key or sequence collision raced past the earlier check; surface the existing record
                if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                    Optional<AuditEvent> existing = eventRepository.findByIdempotencyKey(idempotencyKey);
                    if (existing.isPresent()) {
                        log.info("Idempotent replay resolved after write race for key: {}", idempotencyKey);
                        return mapToResponse(existing.get());
                    }
                }
                throw e;
            }
            log.info("Event created with ID: {} and sequence: {}", savedEvent.getId(), sequenceNumber);
            
            return mapToResponse(savedEvent);
        }
    }
    
    /**
     * Query events with filtering and pagination
     */
    public Page<AuditEventResponse> queryEvents(
        String actorId,
        String eventType,
        String resourceType,
        String resourceId,
        LocalDateTime fromTime,
        LocalDateTime toTime,
        Pageable pageable
    ) {
        log.debug("Querying events with filters: actorId={}, eventType={}, resourceType={}, resourceId={}",
            actorId, eventType, resourceType, resourceId);
        
        Page<AuditEvent> events = eventRepository.findByMultipleCriteria(
            actorId, eventType, resourceType, resourceId, fromTime, toTime, pageable
        );
        
        return events.map(this::mapToResponse);
    }
    
    /**
     * Verify the integrity of the entire audit log hash chain
     */
    @Transactional(readOnly = true)
    public ChainVerificationResponse verifyChain() {
        log.info("Starting chain verification");
        
        List<AuditEvent> allEvents = eventRepository.findAllByOrderBySequenceNumberAsc();
        Integer totalRecords = allEvents.size();
        
        if (totalRecords == 0) {
            log.info("No events to verify");
            return ChainVerificationResponse.builder()
                .isValid(true)
                .totalRecords(0)
                .firstBreach(null)
                .build();
        }
        
        // Verify first record
        AuditEvent firstEvent = allEvents.get(0);
        String expectedFirstContentHash = hashUtil.computeContentHash(
            firstEvent.getEventType(),
            firstEvent.getActorId(),
            firstEvent.getResourceType(),
            firstEvent.getResourceId(),
            firstEvent.getPayload(),
            firstEvent.getTimestamp().toString()
        );

        if (!expectedFirstContentHash.equals(firstEvent.getContentHash())) {
            log.warn("Content hash mismatch detected at first record ID: {}", firstEvent.getId());
            return buildBreachResponse(
                totalRecords,
                firstEvent.getId(),
                expectedFirstContentHash,
                firstEvent.getContentHash(),
                "CONTENT_MODIFIED"
            );
        }

        String expectedFirstChainHash = hashUtil.computeChainHash(
            hashUtil.getGenesisHash(),
            firstEvent.getContentHash()
        );
        
        if (!expectedFirstChainHash.equals(firstEvent.getChainHash())) {
            log.warn("First record chain hash mismatch at record ID: {}", firstEvent.getId());
            return buildBreachResponse(
                totalRecords,
                firstEvent.getId(),
                expectedFirstChainHash,
                firstEvent.getChainHash(),
                "CHAIN_HASH_MISMATCH"
            );
        }
        
        // Verify remaining records
        for (int i = 1; i < allEvents.size(); i++) {
            AuditEvent previousEvent = allEvents.get(i - 1);
            AuditEvent currentEvent = allEvents.get(i);
            
            // Verify chain link
            String expectedChainHash = hashUtil.computeChainHash(
                previousEvent.getChainHash(),
                currentEvent.getContentHash()
            );
            
            if (!expectedChainHash.equals(currentEvent.getChainHash())) {
                log.warn("Chain hash mismatch detected at record ID: {}", currentEvent.getId());
                return buildBreachResponse(
                    totalRecords,
                    currentEvent.getId(),
                    expectedChainHash,
                    currentEvent.getChainHash(),
                    "CHAIN_HASH_MISMATCH"
                );
            }
            
            // Verify content hasn't been modified
            String expectedContentHash = hashUtil.computeContentHash(
                currentEvent.getEventType(),
                currentEvent.getActorId(),
                currentEvent.getResourceType(),
                currentEvent.getResourceId(),
                currentEvent.getPayload(),
                currentEvent.getTimestamp().toString()
            );
            
            if (!expectedContentHash.equals(currentEvent.getContentHash())) {
                log.warn("Content hash mismatch detected at record ID: {}", currentEvent.getId());
                return buildBreachResponse(
                    totalRecords,
                    currentEvent.getId(),
                    expectedContentHash,
                    currentEvent.getContentHash(),
                    "CONTENT_MODIFIED"
                );
            }
        }
        
        log.info("Chain verification successful for {} records", totalRecords);
        return ChainVerificationResponse.builder()
            .isValid(true)
            .totalRecords(totalRecords)
            .firstBreach(null)
            .build();
    }
    
    /**
     * Get a specific event by ID
     */
    public AuditEventResponse getEventById(Long id) {
        return eventRepository.findById(id)
            .map(this::mapToResponse)
            .orElseThrow(() -> new RuntimeException("Event not found with ID: " + id));
    }
    
    /**
     * Get all non-archived events (for admin purposes)
     */
    public List<AuditEventResponse> getAllEvents() {
        return eventRepository.findByIsArchivedFalseOrderBySequenceNumberAsc()
            .stream()
            .map(this::mapToResponse)
            .toList();
    }
    
    /**
     * Map AuditEvent entity to response DTO
     */
    private AuditEventResponse mapToResponse(AuditEvent event) {
        return AuditEventResponse.builder()
            .id(event.getId())
            .eventType(event.getEventType())
            .actorId(event.getActorId())
            .resourceType(event.getResourceType())
            .resourceId(event.getResourceId())
            .payload(event.getPayload())
            .timestamp(event.getTimestamp())
            .contentHash(event.getContentHash())
            .chainHash(event.getChainHash())
            .sequenceNumber(event.getSequenceNumber())
            .isArchived(event.getIsArchived())
            .archivedAt(event.getArchivedAt())
            .createdAt(event.getCreatedAt())
            .build();
    }
    
    /**
     * Build breach response
     */
    private ChainVerificationResponse buildBreachResponse(
        Integer totalRecords,
        Long recordId,
        String expectedHash,
        String actualHash,
        String violationType
    ) {
        return ChainVerificationResponse.builder()
            .isValid(false)
            .totalRecords(totalRecords)
            .firstBreach(ChainVerificationResponse.BreachInfo.builder()
                .recordId(recordId)
                .expectedHash(expectedHash)
                .actualHash(actualHash)
                .violationType(violationType)
                .build())
            .build();
    }
}
