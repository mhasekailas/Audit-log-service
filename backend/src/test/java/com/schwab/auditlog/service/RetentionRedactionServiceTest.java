package com.schwab.auditlog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.auditlog.dto.BulkExportResponse;
import com.schwab.auditlog.dto.RedactionRequest;
import com.schwab.auditlog.dto.RetentionPolicyRequest;
import com.schwab.auditlog.model.AuditEvent;
import com.schwab.auditlog.model.RetentionPolicy;
import com.schwab.auditlog.repository.AuditEventRepository;
import com.schwab.auditlog.repository.RedactionLogRepository;
import com.schwab.auditlog.repository.RetentionPolicyRepository;
import com.schwab.auditlog.util.HashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetentionRedactionServiceTest {
    @Mock AuditEventRepository eventRepository;
    @Mock RedactionLogRepository redactionLogRepository;
    @Mock RetentionPolicyRepository retentionPolicyRepository;

    private RetentionRedactionService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new RetentionRedactionService(eventRepository, redactionLogRepository,
            retentionPolicyRepository, new HashUtil(objectMapper), objectMapper);
    }

    @Test
    void redactionReplacesNestedValueAndRebuildsHashes() throws Exception {
        AuditEvent event = event(1L, 1L, "user-1", "resource-1", "{\"customer\":{\"accountNumber\":\"1234\"}}");
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRepository.findAllByOrderBySequenceNumberAsc()).thenReturn(List.of(event));

        RedactionRequest request = new RedactionRequest();
        request.setFieldPaths(List.of("customer.accountNumber"));
        request.setReason("privacy request");

        service.redact(1L, request);

        assertEquals("[REDACTED]", event.getPayload().at("/customer/accountNumber").asText());
        assertNotNull(event.getContentHash());
        assertNotNull(event.getChainHash());
        verify(redactionLogRepository).save(any());
        verify(eventRepository, atLeastOnce()).save(event);
    }

    @Test
    void archiveExpiredArchivesOnlyMatchingOldPolicyRecords() {
        AuditEvent oldEvent = event(1L, 1L, "user-1", "resource-1", "{}");
        oldEvent.setResourceType("ACCOUNT");
        oldEvent.setTimestamp(LocalDateTime.now().minusDays(40));
        RetentionPolicy policy = RetentionPolicy.builder().resourceType("ACCOUNT")
            .retentionDays(30).archiveOnExpiry(true).build();
        when(retentionPolicyRepository.findAll()).thenReturn(List.of(policy));
        when(eventRepository.findByTimestampBeforeAndIsArchivedFalse(any())).thenReturn(List.of(oldEvent));

        int archived = service.archiveExpired(LocalDateTime.now());

        assertEquals(1, archived);
        assertTrue(oldEvent.getIsArchived());
        assertNotNull(oldEvent.getArchivedAt());
        verify(eventRepository).save(oldEvent);
    }

    @Test
    void exportIncludesPredecessorChainMetadata() {
        AuditEvent predecessor = event(1L, 1L, "other", "resource-0", "{}");
        predecessor.setChainHash("previous-chain");
        AuditEvent selected = event(2L, 2L, "user-1", "resource-1", "{}");
        HashUtil verifier = new HashUtil(objectMapper);
        selected.setContentHash(verifier.computeContentHash(
            selected.getEventType(), selected.getActorId(), selected.getResourceType(),
            selected.getResourceId(), selected.getPayload(), selected.getTimestamp().toString()));
        selected.setChainHash(verifier.computeChainHash("previous-chain", selected.getContentHash()));
        when(eventRepository.findByActorIdOrderBySequenceNumberAsc("user-1"))
            .thenReturn(List.of(selected));
        when(eventRepository.findAllByOrderBySequenceNumberAsc())
            .thenReturn(List.of(predecessor, selected));

        BulkExportResponse export = service.export("user-1", null);

        assertEquals("ACTOR_ID", export.getExportType());
        assertEquals("previous-chain", export.getPredecessorChainHash());
        assertEquals("previous-chain", export.getRecords().get(0).getPreviousChainHash());
        assertEquals("SHA-256; contentHash=event fields; chainHash=previousChainHash+contentHash", export.getAlgorithm());
        assertEquals(selected.getContentHash(), verifier.computeContentHash(
            export.getRecords().get(0).getEventType(), export.getRecords().get(0).getActorId(),
            export.getRecords().get(0).getResourceType(), export.getRecords().get(0).getResourceId(),
            export.getRecords().get(0).getPayload(), export.getRecords().get(0).getTimestamp().toString()));
        assertEquals(selected.getChainHash(), verifier.computeChainHash(
            export.getRecords().get(0).getPreviousChainHash(), export.getRecords().get(0).getContentHash()));
    }

    private AuditEvent event(Long id, Long sequence, String actor, String resource, String payload) {
        try {
            return AuditEvent.builder()
                .id(id).sequenceNumber(sequence).eventType("TEST")
                .actorId(actor).resourceType("ACCOUNT").resourceId(resource)
                .payload(objectMapper.readTree(payload)).timestamp(LocalDateTime.now().withNano(0))
                .contentHash("old-content").chainHash("old-chain").isArchived(false)
                .createdAt(LocalDateTime.now()).build();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
