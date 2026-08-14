package com.schwab.auditlog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ComplianceAccessRequest {
    @NotNull
    private Long auditEventId;
    @NotBlank
    private String accessType;
    private String userRole;
    private String ipAddress;
    private String userAgent;
    @NotBlank
    private String accessResult;
}
