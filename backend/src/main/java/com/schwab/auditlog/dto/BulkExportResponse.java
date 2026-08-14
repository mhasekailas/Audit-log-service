package com.schwab.auditlog.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class BulkExportResponse {
    String exportType;
    String exportValue;
    String algorithm;
    String genesisHash;
    String predecessorChainHash;
    LocalDateTime exportedAt;
    List<ExportRecord> records;

    @Value
    @Builder
    public static class ExportRecord {
        Long id;
        Long sequenceNumber;
        String eventType;
        String actorId;
        String resourceType;
        String resourceId;
        JsonNode payload;
        LocalDateTime timestamp;
        String contentHash;
        String chainHash;
        String previousChainHash;
    }
}
