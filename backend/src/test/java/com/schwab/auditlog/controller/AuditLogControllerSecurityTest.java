package com.schwab.auditlog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security/authorization matrix tests for the audit API.
 * Verifies: missing credentials -> 401, wrong role -> 403, correct role -> success,
 * and Idempotency-Key replay protection.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditLogControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String READER = "test-reader";
    private static final String READER_PW = "reader-pass-123";
    private static final String WRITER = "test-writer";
    private static final String WRITER_PW = "writer-pass-123";
    private static final String ADMIN = "test-admin";
    private static final String ADMIN_PW = "admin-pass-123";

    private String createEventBody() {
        return "{\"eventType\":\"LOGIN\",\"actorId\":\"actor-sec-1\",\"resourceType\":\"ACCOUNT\"," +
            "\"resourceId\":\"acct-sec-1\",\"payload\":{\"ok\":true}}";
    }

    // --- Health / public endpoint ---

    @Test
    @WithAnonymousUser
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/audit/health"))
            .andExpect(status().isOk());
    }

    // --- No credentials => 401 ---

    @Test
    void createEventWithoutCredentialsIsUnauthorized() throws Exception {
        mockMvc.perform(post("/audit/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEventBody()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void queryEventsWithoutCredentialsIsUnauthorized() throws Exception {
        mockMvc.perform(get("/audit/events"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyChainWithoutCredentialsIsUnauthorized() throws Exception {
        mockMvc.perform(get("/audit/verify"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void complianceReportWithoutCredentialsIsUnauthorized() throws Exception {
        mockMvc.perform(get("/audit/compliance-report"))
            .andExpect(status().isUnauthorized());
    }

    // --- Wrong/insufficient role => 403 ---

    @Test
    void readerCannotCreateEvents() throws Exception {
        mockMvc.perform(post("/audit/events")
                .with(httpBasic(READER, READER_PW))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEventBody()))
            .andExpect(status().isForbidden());
    }

    @Test
    void writerCannotAccessComplianceReport() throws Exception {
        mockMvc.perform(get("/audit/compliance-report")
                .with(httpBasic(WRITER, WRITER_PW)))
            .andExpect(status().isForbidden());
    }

    @Test
    void writerCannotConfigureRetentionPolicy() throws Exception {
        mockMvc.perform(post("/audit/retention-policies")
                .with(httpBasic(WRITER, WRITER_PW))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resourceType\":\"ACCOUNT\",\"retentionDays\":30}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void invalidPasswordIsUnauthorized() throws Exception {
        mockMvc.perform(get("/audit/events")
                .with(httpBasic(READER, "wrong-password")))
            .andExpect(status().isUnauthorized());
    }

    // --- Correct role => success ---

    @Test
    void readerCanQueryEvents() throws Exception {
        mockMvc.perform(get("/audit/events")
                .with(httpBasic(READER, READER_PW)))
            .andExpect(status().isOk());
    }

    @Test
    void readerCanVerifyChain() throws Exception {
        mockMvc.perform(get("/audit/verify")
                .with(httpBasic(READER, READER_PW)))
            .andExpect(status().isOk());
    }

    @Test
    void writerCanCreateEvents() throws Exception {
        mockMvc.perform(post("/audit/events")
                .with(httpBasic(WRITER, WRITER_PW))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEventBody()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void adminCanAccessComplianceReport() throws Exception {
        mockMvc.perform(get("/audit/compliance-report")
                .with(httpBasic(ADMIN, ADMIN_PW)))
            .andExpect(status().isOk());
    }

    @Test
    void adminCanConfigureRetentionPolicy() throws Exception {
        mockMvc.perform(post("/audit/retention-policies")
                .with(httpBasic(ADMIN, ADMIN_PW))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resourceType\":\"ADMIN_TEST\",\"retentionDays\":30}"))
            .andExpect(status().isOk());
    }

    // --- Replay / idempotency protection ---

    @Test
    void duplicateIdempotencyKeyReturnsOriginalEventInsteadOfDuplicate() throws Exception {
        String body = "{\"eventType\":\"LOGIN\",\"actorId\":\"actor-replay\",\"resourceType\":\"ACCOUNT\"," +
            "\"resourceId\":\"acct-replay\",\"payload\":{\"ok\":true}}";

        String firstResponse = mockMvc.perform(post("/audit/events")
                .with(httpBasic(WRITER, WRITER_PW))
                .header("Idempotency-Key", "replay-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.replay").value(false))
            .andReturn().getResponse().getContentAsString();

        Long firstId = objectMapper.readTree(firstResponse).path("data").path("id").asLong();

        mockMvc.perform(post("/audit/events")
                .with(httpBasic(WRITER, WRITER_PW))
                .header("Idempotency-Key", "replay-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.replay").value(true))
            .andExpect(jsonPath("$.data.id").value(firstId));
    }

    // Rate limiting itself is unit-tested in isolation in RateLimitingFilterTest;
    // this shared Spring context serves many tests with the same credentials, so
    // exercising the limiter here would make quotas flaky across the whole suite.
}
