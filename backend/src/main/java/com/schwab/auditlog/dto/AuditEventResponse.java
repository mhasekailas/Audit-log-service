package com.schwab.auditlog.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * DTO for returning audit events (Read API response)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEventResponse {
    
    private Long id;
    private String eventType;
    private String actorId;
    private String resourceType;
    private String resourceId;
    private JsonNode payload;
    private LocalDateTime timestamp;
    private String contentHash;
    private String chainHash;
    private Long sequenceNumber;
    private Boolean isArchived;
    private LocalDateTime archivedAt;
    private LocalDateTime createdAt;
}
