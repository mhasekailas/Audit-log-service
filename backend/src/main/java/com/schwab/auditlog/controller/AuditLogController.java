package com.schwab.auditlog.controller;

import com.schwab.auditlog.dto.AuditEventResponse;
import com.schwab.auditlog.dto.ChainVerificationResponse;
import com.schwab.auditlog.dto.CreateEventRequest;
import com.schwab.auditlog.service.AuditEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for Audit Log Service API
 * Handles all audit event operations: create, query, and verify
 */
@RestController
@RequestMapping("/audit")
@Validated
@Slf4j
@Tag(name = "Audit Log API", description = "Tamper-evident audit log service API")
public class AuditLogController {
    
    private final AuditEventService eventService;
    
    public AuditLogController(AuditEventService eventService) {
        this.eventService = eventService;
    }
    
    /**
     * Create a new audit event (Write API)
     */
    @PostMapping("/events")
    @Operation(summary = "Create a new audit event",
               description = "Append a new event to the audit log. Hash chain is computed automatically.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Event created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid event data"),
        @ApiResponse(responseCode = "500", description = "Server error")
    })
    public ResponseEntity<Map<String, Object>> createEvent(
        @Valid @RequestBody CreateEventRequest request
    ) {
        log.info("POST /audit/events - Creating event: {}", request.getEventType());
        
        try {
            AuditEventResponse response = eventService.createEvent(request);
            
            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("data", response);
            body.put("message", "Event created successfully");
            
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (Exception e) {
            log.error("Error creating event", e);
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("success", false);
            errorBody.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody);
        }
    }
    
    /**
     * Query audit events with filtering and pagination (Query API)
     */
    @GetMapping("/events")
    @Operation(summary = "Query audit events",
               description = "Retrieve events with optional filtering by actor, event type, resource, and time range. Supports pagination.")
    @ApiResponse(responseCode = "200", description = "Events retrieved successfully")
    public ResponseEntity<Map<String, Object>> queryEvents(
        @Parameter(description = "Filter by actor ID")
        @RequestParam(required = false) String actorId,
        
        @Parameter(description = "Filter by event type")
        @RequestParam(required = false) String eventType,
        
        @Parameter(description = "Filter by resource type")
        @RequestParam(required = false) String resourceType,
        
        @Parameter(description = "Filter by resource ID")
        @RequestParam(required = false) String resourceId,
        
        @Parameter(description = "Filter from timestamp (ISO format)")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime fromTime,
        
        @Parameter(description = "Filter to timestamp (ISO format)")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime toTime,
        
        @Parameter(description = "Page number (0-indexed, default: 0)")
        @RequestParam(defaultValue = "0") int page,
        
        @Parameter(description = "Page size (default: 50)")
        @RequestParam(defaultValue = "50") int limit
    ) {
        log.info("GET /audit/events - Query with filters: actorId={}, eventType={}", actorId, eventType);
        
        try {
            Pageable pageable = PageRequest.of(page, limit);
            Page<AuditEventResponse> events = eventService.queryEvents(
                actorId, eventType, resourceType, resourceId, fromTime, toTime, pageable
            );
            
            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("records", events.getContent());
            body.put("totalCount", events.getTotalElements());
            body.put("pageNumber", events.getNumber());
            body.put("pageSize", events.getSize());
            body.put("hasMore", events.hasNext());
            
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("Error querying events", e);
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("success", false);
            errorBody.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody);
        }
    }
    
    /**
     * Verify chain integrity (Scenario A - core feature)
     */
    @GetMapping("/verify")
    @Operation(summary = "Verify audit log chain integrity",
               description = "Walk the entire hash chain and verify that no records have been tampered with. " +
                           "Detects any modifications to event content or chain integrity violations.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Chain verification completed (valid or invalid)"),
        @ApiResponse(responseCode = "500", description = "Server error during verification")
    })
    public ResponseEntity<Map<String, Object>> verifyChain() {
        log.info("GET /audit/verify - Starting chain verification");
        
        try {
            ChainVerificationResponse result = eventService.verifyChain();
            
            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("data", result);
            
            if (result.getIsValid()) {
                body.put("message", "Chain integrity verified - no tampering detected");
            } else {
                body.put("message", "Chain integrity violation detected");
            }
            
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("Error verifying chain", e);
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("success", false);
            errorBody.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody);
        }
    }
    
    /**
     * Get a specific event by ID
     */
    @GetMapping("/events/{id}")
    @Operation(summary = "Get event by ID",
               description = "Retrieve a specific audit event by its ID")
    @ApiResponse(responseCode = "200", description = "Event retrieved successfully")
    public ResponseEntity<Map<String, Object>> getEventById(
        @Parameter(description = "Event ID")
        @PathVariable Long id
    ) {
        log.info("GET /audit/events/{} - Fetching event", id);
        
        try {
            AuditEventResponse event = eventService.getEventById(id);
            
            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("data", event);
            
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("Error fetching event", e);
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("success", false);
            errorBody.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody);
        }
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Verify the service is running")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Audit Log Service");
        return ResponseEntity.ok(response);
    }
}
