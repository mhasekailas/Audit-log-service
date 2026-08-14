package com.schwab.auditlog.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Value
@Builder
public class ComplianceReportResponse {
    LocalDateTime from;
    LocalDateTime to;
    String actorId;
    String resourceId;
    String accessType;
    long totalAccesses;
    long successfulAccesses;
    long deniedAccesses;
    Map<String, Long> accessesByType;
    List<AccessRecord> records;

    @Value
    @Builder
    public static class AccessRecord {
        Long accessId;
        Long auditEventId;
        String eventType;
        String actorId;
        String resourceType;
        String resourceId;
        String accessType;
        String userRole;
        String ipAddress;
        String userAgent;
        String accessResult;
        LocalDateTime accessedAt;
    }
}
