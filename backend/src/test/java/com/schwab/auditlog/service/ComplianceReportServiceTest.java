package com.schwab.auditlog.service;

import com.schwab.auditlog.dto.ComplianceAccessRequest;
import com.schwab.auditlog.dto.ComplianceReportResponse;
import com.schwab.auditlog.model.AuditEvent;
import com.schwab.auditlog.model.ComplianceAuditAccess;
import com.schwab.auditlog.repository.AuditEventRepository;
import com.schwab.auditlog.repository.ComplianceAuditAccessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplianceReportServiceTest {
    @Mock ComplianceAuditAccessRepository accessRepository;
    @Mock AuditEventRepository eventRepository;

    private ComplianceReportService service;

    @BeforeEach
    void setUp() {
        service = new ComplianceReportService(accessRepository, eventRepository);
    }

    @Test
    void recordsAccessAndNormalizesDecisionFields() {
        ComplianceAccessRequest request = new ComplianceAccessRequest();
        request.setAuditEventId(7L);
        request.setAccessType("read");
        request.setAccessResult("success");
        request.setUserRole("REGULATOR");
        when(eventRepository.existsById(7L)).thenReturn(true);
        when(accessRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ComplianceAuditAccess saved = service.recordAccess(request);

        assertEquals("READ", saved.getAccessType());
        assertEquals("SUCCESS", saved.getAccessResult());
        assertEquals("REGULATOR", saved.getUserRole());
    }

    @Test
    void reportAggregatesSuccessDeniedAndAccessTypes() {
        LocalDateTime accessedAt = LocalDateTime.of(2026, 8, 14, 10, 0);
        ComplianceAuditAccess success = access(1L, 10L, "READ", "SUCCESS", accessedAt);
        ComplianceAuditAccess denied = access(2L, 11L, "READ", "DENIED", accessedAt.plusMinutes(1));
        AuditEvent eventOne = auditEvent(10L, "actor-1", "acct-1");
        AuditEvent eventTwo = auditEvent(11L, "actor-1", "acct-1");
        when(accessRepository.findReportRecords(any(), any(), eq("actor-1"), eq("acct-1"), isNull()))
            .thenReturn(List.of(success, denied));
        when(eventRepository.findAllById(List.of(10L, 11L))).thenReturn(List.of(eventOne, eventTwo));

        ComplianceReportResponse report = service.report(null, null, "actor-1", "acct-1", null);

        assertEquals(2, report.getTotalAccesses());
        assertEquals(1, report.getSuccessfulAccesses());
        assertEquals(1, report.getDeniedAccesses());
        assertEquals(2L, report.getAccessesByType().get("READ"));
        assertEquals(2, report.getRecords().size());
    }

    @Test
    void rejectsAccessForUnknownAuditEvent() {
        ComplianceAccessRequest request = new ComplianceAccessRequest();
        request.setAuditEventId(999L);
        request.setAccessType("READ");
        request.setAccessResult("SUCCESS");
        when(eventRepository.existsById(999L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.recordAccess(request));
        verifyNoInteractions(accessRepository);
    }

    private ComplianceAuditAccess access(Long id, Long eventId, String type, String result, LocalDateTime createdAt) {
        return ComplianceAuditAccess.builder().id(id).auditEventId(eventId)
            .accessType(type).accessResult(result).createdAt(createdAt).build();
    }

    private AuditEvent auditEvent(Long id, String actor, String resource) {
        return AuditEvent.builder().id(id).actorId(actor).resourceId(resource)
            .eventType("ACCOUNT_READ").resourceType("ACCOUNT").build();
    }
}
