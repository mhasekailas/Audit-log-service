package com.schwab.auditlog.service;

import com.schwab.auditlog.dto.ComplianceAccessRequest;
import com.schwab.auditlog.dto.ComplianceReportResponse;
import com.schwab.auditlog.model.AuditEvent;
import com.schwab.auditlog.model.ComplianceAuditAccess;
import com.schwab.auditlog.repository.AuditEventRepository;
import com.schwab.auditlog.repository.ComplianceAuditAccessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ComplianceReportService {
    private final ComplianceAuditAccessRepository accessRepository;
    private final AuditEventRepository eventRepository;

    public ComplianceReportService(ComplianceAuditAccessRepository accessRepository,
                                   AuditEventRepository eventRepository) {
        this.accessRepository = accessRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public ComplianceAuditAccess recordAccess(ComplianceAccessRequest request) {
        if (!eventRepository.existsById(request.getAuditEventId())) {
            throw new IllegalArgumentException("Audit event not found: " + request.getAuditEventId());
        }
        return accessRepository.save(ComplianceAuditAccess.builder()
            .auditEventId(request.getAuditEventId())
            .accessType(request.getAccessType().toUpperCase())
            .userRole(request.getUserRole())
            .ipAddress(request.getIpAddress())
            .userAgent(request.getUserAgent())
            .accessResult(request.getAccessResult().toUpperCase())
            .build());
    }

    @Transactional(readOnly = true)
    public ComplianceReportResponse report(LocalDateTime from, LocalDateTime to,
                                           String actorId, String resourceId, String accessType) {
        List<ComplianceAuditAccess> accesses = accessRepository.findReportRecords(
            from, to, actorId, resourceId, accessType == null ? null : accessType.toUpperCase());
        Map<Long, AuditEvent> events = eventRepository.findAllById(
            accesses.stream().map(ComplianceAuditAccess::getAuditEventId).toList())
            .stream().collect(Collectors.toMap(AuditEvent::getId, Function.identity()));
        List<ComplianceReportResponse.AccessRecord> records = accesses.stream().map(access -> {
            AuditEvent event = events.get(access.getAuditEventId());
            return ComplianceReportResponse.AccessRecord.builder()
                .accessId(access.getId()).auditEventId(access.getAuditEventId())
                .eventType(event == null ? null : event.getEventType())
                .actorId(event == null ? null : event.getActorId())
                .resourceType(event == null ? null : event.getResourceType())
                .resourceId(event == null ? null : event.getResourceId())
                .accessType(access.getAccessType()).userRole(access.getUserRole())
                .ipAddress(access.getIpAddress()).userAgent(access.getUserAgent())
                .accessResult(access.getAccessResult()).accessedAt(access.getCreatedAt())
                .build();
        }).toList();
        Map<String, Long> byType = records.stream().collect(Collectors.groupingBy(
            ComplianceReportResponse.AccessRecord::getAccessType, Collectors.counting()));
        return ComplianceReportResponse.builder()
            .from(from).to(to).actorId(actorId).resourceId(resourceId).accessType(accessType)
            .totalAccesses(records.size())
            .successfulAccesses(records.stream().filter(r -> "SUCCESS".equals(r.getAccessResult())).count())
            .deniedAccesses(records.stream().filter(r -> "DENIED".equals(r.getAccessResult())).count())
            .accessesByType(byType).records(records).build();
    }
}
