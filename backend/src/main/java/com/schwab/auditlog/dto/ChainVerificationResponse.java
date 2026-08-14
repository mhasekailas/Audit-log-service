package com.schwab.auditlog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for chain verification response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChainVerificationResponse {
    
    private Boolean isValid;
    private Integer totalRecords;
    private BreachInfo firstBreach;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BreachInfo {
        private Long recordId;
        private String expectedHash;
        private String actualHash;
        private String violationType;
    }
}
