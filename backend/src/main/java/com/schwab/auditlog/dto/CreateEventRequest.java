package com.schwab.auditlog.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * DTO for creating new audit events (Write API request)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateEventRequest {
    
    @NotBlank(message = "eventType is required")
    private String eventType;
    
    @NotBlank(message = "actorId is required")
    private String actorId;
    
    @NotBlank(message = "resourceType is required")
    private String resourceType;
    
    @NotBlank(message = "resourceId is required")
    private String resourceId;
    
    @NotNull(message = "payload is required")
    private JsonNode payload;
    
    // Optional: if not provided, server will assign current timestamp
    private LocalDateTime timestamp;
}
