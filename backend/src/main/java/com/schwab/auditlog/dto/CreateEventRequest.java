package com.schwab.auditlog.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @Size(max = 50, message = "eventType must be at most 50 characters")
    private String eventType;
    
    @NotBlank(message = "actorId is required")
    @Size(max = 255, message = "actorId must be at most 255 characters")
    private String actorId;
    
    @NotBlank(message = "resourceType is required")
    @Size(max = 100, message = "resourceType must be at most 100 characters")
    private String resourceType;
    
    @NotBlank(message = "resourceId is required")
    @Size(max = 255, message = "resourceId must be at most 255 characters")
    private String resourceId;
    
    @NotNull(message = "payload is required")
    private JsonNode payload;
    
    // Optional: if not provided, server will assign current timestamp
    private LocalDateTime timestamp;
}
