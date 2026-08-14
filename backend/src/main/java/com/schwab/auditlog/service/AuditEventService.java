package com.schwab.auditlog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.schwab.auditlog.dto.AuditEventResponse;
import com.schwab.auditlog.dto.ChainVerificationResponse;
import com.schwab.auditlog.dto.CreateEventRequest;
import com.schwab.auditlog.model.AuditEvent;
import com.schwab.auditlog.repository.AuditEventRepository;
import com.schwab.auditlog.util.HashUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final HashUtil hashUtil;
    
    public AuditEventService(AuditEventRepository eventRepository, HashUtil hashUtil) {
        this.eventRepository = eventRepository;
        this.hashUtil = hashUtil;
    }
    
    /**
     * Create and store a new audit event with hash chain
     */
    @Transactional
    public AuditEventResponse createEvent(CreateEventRequest request) {
        log.debug("Creating event: {} for actor: {}", request.getEventType(), request.getActorId());
        
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
            .isArchived(false)
            .build();
        
        AuditEvent savedEvent = eventRepository.save(event);
        log.info("Event created with ID: {} and sequence: {}", savedEvent.getId(), sequenceNumber);
        
        return mapToResponse(savedEvent);
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
