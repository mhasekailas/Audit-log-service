package com.schwab.auditlog.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class RetentionPolicyRequest {
    private String resourceType;

    @Min(1)
    private Integer retentionDays;

    private Boolean archiveOnExpiry = true;
}
