package com.schwab.auditlog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Audit Log Service application.
 * 
 * Features:
 * - Tamper-evident audit logging with hash chain verification
 * - Append-only event storage
 * - Compliance reporting and data redaction
 */
@SpringBootApplication
public class AuditLogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditLogServiceApplication.class, args);
    }

}
