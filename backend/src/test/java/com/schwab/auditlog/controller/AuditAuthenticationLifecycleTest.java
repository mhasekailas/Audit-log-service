package com.schwab.auditlog.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "AUDIT_READER_USERNAME=expired-reader",
    "AUDIT_READER_PASSWORD=reader-pass-123",
    "AUDIT_READER_ENABLED=true",
    "AUDIT_READER_EXPIRES_AT=2000-01-01T00:00:00Z",
    "AUDIT_API_USERNAME=revoked-writer",
    "AUDIT_API_PASSWORD=writer-pass-123",
    "AUDIT_API_ENABLED=false",
    "AUDIT_API_EXPIRES_AT=",
    "AUDIT_ADMIN_USERNAME=expired-admin",
    "AUDIT_ADMIN_PASSWORD=admin-pass-123",
    "AUDIT_ADMIN_ENABLED=true",
    "AUDIT_ADMIN_EXPIRES_AT=2000-01-01T00:00:00Z"
})
class AuditAuthenticationLifecycleTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void expiredReaderCannotReadAuditEvents() throws Exception {
        mockMvc.perform(get("/audit/events")
                .with(httpBasic("expired-reader", "reader-pass-123")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void revokedWriterCannotWriteRedactOrArchive() throws Exception {
        String createBody = "{\"eventType\":\"LOGIN\",\"actorId\":\"revoked-writer\"," +
            "\"resourceType\":\"ACCOUNT\",\"resourceId\":\"acct-revoked\",\"payload\":{\"ok\":true}}";
        String redactBody = "{\"fieldPaths\":[\"secret\"],\"reason\":\"test\"}";

        mockMvc.perform(post("/audit/events")
                .with(httpBasic("revoked-writer", "writer-pass-123"))
                .header("Idempotency-Key", "revoked-writer-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/audit/redact/1")
                .with(httpBasic("revoked-writer", "writer-pass-123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(redactBody))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/audit/archive")
                .with(httpBasic("revoked-writer", "writer-pass-123")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredAdminCannotUseAdminEndpoints() throws Exception {
        mockMvc.perform(post("/audit/retention-policies")
                .with(httpBasic("expired-admin", "admin-pass-123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resourceType\":\"ACCOUNT\",\"retentionDays\":30}"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/audit/compliance-report")
                .with(httpBasic("expired-admin", "admin-pass-123")))
            .andExpect(status().isUnauthorized());
    }
}
