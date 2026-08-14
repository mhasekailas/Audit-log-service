package com.schwab.auditlog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.auditlog.dto.ChainVerificationResponse;
import com.schwab.auditlog.dto.CreateEventRequest;
import com.schwab.auditlog.model.AuditEvent;
import com.schwab.auditlog.repository.AuditEventRepository;
import com.schwab.auditlog.util.HashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditEventServiceTest {
    @Mock AuditEventRepository eventRepository;

    private AuditEventService service;
    private HashUtil hashUtil;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        hashUtil = new HashUtil(objectMapper);
        service = new AuditEventService(eventRepository, hashUtil);
    }

    @Test
    void createEventUsesGenesisForFirstRecordAndServerTimestamp() throws Exception {
        CreateEventRequest request = request("LOGIN", "actor-1", "ACCOUNT", "acct-1", "{\"ok\":true}");
        when(eventRepository.getNextSequenceNumber()).thenReturn(1L);
        when(eventRepository.findLastRecord()).thenReturn(Optional.empty());
        when(eventRepository.save(any(AuditEvent.class))).thenAnswer(invocation -> {
            AuditEvent event = invocation.getArgument(0);
            event.setId(1L);
            event.setCreatedAt(event.getTimestamp());
            return event;
        });

        var response = service.createEvent(request);

        assertEquals(1L, response.getSequenceNumber());
        assertNotNull(response.getTimestamp());
        assertEquals(hashUtil.computeChainHash(hashUtil.getGenesisHash(), response.getContentHash()), response.getChainHash());
    }

    @Test
    void verifyChainAcceptsCleanChainAndEmptyChain() {
        when(eventRepository.findAllByOrderBySequenceNumberAsc()).thenReturn(List.of());
        ChainVerificationResponse empty = service.verifyChain();
        assertTrue(empty.getIsValid());
        assertEquals(0, empty.getTotalRecords());

        AuditEvent first = event(1L, 1L, "actor-1", "acct-1", "{}");
        AuditEvent second = event(2L, 2L, "actor-1", "acct-2", "{}");
        hashChain(first, hashUtil.getGenesisHash());
        hashChain(second, first.getChainHash());
        when(eventRepository.findAllByOrderBySequenceNumberAsc()).thenReturn(List.of(first, second));

        ChainVerificationResponse valid = service.verifyChain();
        assertTrue(valid.getIsValid());
        assertEquals(2, valid.getTotalRecords());
    }

    @Test
    void verifyChainAcceptsLegitimatelyArchivedRecord() {
        AuditEvent archived = event(1L, 1L, "actor-1", "acct-1", "{}");
        archived.setIsArchived(true);
        AuditEvent active = event(2L, 2L, "actor-1", "acct-2", "{}");
        hashChain(archived, hashUtil.getGenesisHash());
        hashChain(active, archived.getChainHash());
        when(eventRepository.findAllByOrderBySequenceNumberAsc()).thenReturn(List.of(archived, active));

        ChainVerificationResponse result = service.verifyChain();

        assertTrue(result.getIsValid());
        assertEquals(2, result.getTotalRecords());
    }

    @Test
    void verifyChainDetectsFirstRecordContentTampering() {
        AuditEvent first = event(1L, 1L, "actor-1", "acct-1", "{}");
        hashChain(first, hashUtil.getGenesisHash());
        first.setPayload(objectMapper.createObjectNode().put("tampered", true));
        when(eventRepository.findAllByOrderBySequenceNumberAsc()).thenReturn(List.of(first));

        ChainVerificationResponse result = service.verifyChain();

        assertFalse(result.getIsValid());
        assertEquals(1L, result.getFirstBreach().getRecordId());
        assertEquals("CONTENT_MODIFIED", result.getFirstBreach().getViolationType());
    }

    @Test
    void verifyChainDetectsLaterChainBreak() {
        AuditEvent first = event(1L, 1L, "actor-1", "acct-1", "{}");
        AuditEvent second = event(2L, 2L, "actor-1", "acct-2", "{}");
        hashChain(first, hashUtil.getGenesisHash());
        hashChain(second, first.getChainHash());
        second.setChainHash("invalid-chain-hash");
        when(eventRepository.findAllByOrderBySequenceNumberAsc()).thenReturn(List.of(first, second));

        ChainVerificationResponse result = service.verifyChain();

        assertFalse(result.getIsValid());
        assertEquals(2L, result.getFirstBreach().getRecordId());
        assertEquals("CHAIN_HASH_MISMATCH", result.getFirstBreach().getViolationType());
    }

    private CreateEventRequest request(String type, String actor, String resourceType, String resourceId, String payload) throws Exception {
        CreateEventRequest request = new CreateEventRequest();
        request.setEventType(type);
        request.setActorId(actor);
        request.setResourceType(resourceType);
        request.setResourceId(resourceId);
        request.setPayload(objectMapper.readTree(payload));
        return request;
    }

    private AuditEvent event(Long id, Long sequence, String actor, String resourceId, String payload) {
        try {
            return AuditEvent.builder().id(id).sequenceNumber(sequence).eventType("LOGIN")
                .actorId(actor).resourceType("ACCOUNT").resourceId(resourceId)
                .payload(objectMapper.readTree(payload)).timestamp(LocalDateTime.of(2026, 8, 14, 10, 0))
                .isArchived(false).createdAt(LocalDateTime.of(2026, 8, 14, 10, 0)).build();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void hashChain(AuditEvent event, String previousChainHash) {
        event.setContentHash(hashUtil.computeContentHash(event.getEventType(), event.getActorId(),
            event.getResourceType(), event.getResourceId(), event.getPayload(), event.getTimestamp().toString()));
        event.setChainHash(hashUtil.computeChainHash(previousChainHash, event.getContentHash()));
    }
}
