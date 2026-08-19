package com.schwab.auditlog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.List;
import java.util.UUID;

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
        return "{\"eventType\":\"LOGIN\",\"actorId\":\"test-writer\",\"resourceType\":\"ACCOUNT\"," +
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
    void auditRootWriteWithoutCredentialsIsUnauthorized() throws Exception {
        mockMvc.perform(post("/audit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void auditRootWriteWithWriterIsForbidden() throws Exception {
        mockMvc.perform(post("/audit")
                .with(httpBasic(WRITER, WRITER_PW))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void rootAuditReadWithoutCredentialsIsUnauthorized() throws Exception {
        mockMvc.perform(get("/audit"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void auditSearchWithoutCredentialsIsUnauthorized() throws Exception {
        mockMvc.perform(get("/audit/search"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyEndpointWithoutCredentialsIsUnauthorized() throws Exception {
        mockMvc.perform(get("/audit/verify"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void archiveEndpointWithoutCredentialsIsUnauthorized() throws Exception {
        mockMvc.perform(post("/audit/archive"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void redactEndpointWithoutCredentialsIsUnauthorized() throws Exception {
        mockMvc.perform(post("/audit/redact/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fieldPaths\":[\"secret\"],\"reason\":\"test\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void exportEndpointWithoutCredentialsIsUnauthorized() throws Exception {
        mockMvc.perform(get("/audit/export"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void payloadEndpointWithoutCredentialsIsUnauthorized() throws Exception {
        mockMvc.perform(get("/audit/1/payload"))
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

    @Test
    void sensitiveAuditApisRequireAuthentication() throws Exception {
        String redactionBody = "{\"fieldPaths\":[\"secret\"],\"reason\":\"test\"}";
        String retentionPolicyBody = "{\"resourceType\":\"ACCOUNT\",\"retentionDays\":30}";
        String complianceAccessBody = "{\"auditEventId\":1,\"accessType\":\"READ\"," +
            "\"userRole\":\"ANALYST\",\"ipAddress\":\"127.0.0.1\",\"userAgent\":\"junit\"," +
            "\"accessResult\":\"SUCCESS\"}";

        List<RequestBuilder> sensitiveRequests = List.of(
            get("/audit"),
            get("/audit/search"),
            get("/audit/events"),
            get("/audit/events/1"),
            get("/audit/verify"),
            get("/audit/export"),
            get("/audit/compliance-report"),
            post("/audit/events").contentType(MediaType.APPLICATION_JSON).content(createEventBody()),
            post("/audit/retention-policies").contentType(MediaType.APPLICATION_JSON).content(retentionPolicyBody),
            post("/audit/retention/archive"),
            post("/audit/archive"),
            post("/audit/events/1/redact").contentType(MediaType.APPLICATION_JSON).content(redactionBody),
            post("/audit/redact/1").contentType(MediaType.APPLICATION_JSON).content(redactionBody),
            post("/audit/compliance/access").contentType(MediaType.APPLICATION_JSON).content(complianceAccessBody)
        );

        for (RequestBuilder request : sensitiveRequests) {
            mockMvc.perform(request)
                .andExpect(status().isUnauthorized());
        }
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
    void readerCannotRedactPayload() throws Exception {
        mockMvc.perform(post("/audit/redact/1")
                .with(httpBasic(READER, READER_PW))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fieldPaths\":[\"secret\"],\"reason\":\"test\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void writerCannotRedactAnotherActorsEvent() throws Exception {
        String body = "{\"eventType\":\"LOGIN\",\"actorId\":\"other-actor\",\"resourceType\":\"ACCOUNT\"," +
            "\"resourceId\":\"acct-redact-other\",\"payload\":{\"secret\":\"value\"}}";

        String response = mockMvc.perform(post("/audit/events")
                .with(httpBasic(ADMIN, ADMIN_PW))
                .header("Idempotency-Key", "other-actor-redact-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long eventId = objectMapper.readTree(response).path("data").path("id").asLong();

        mockMvc.perform(post("/audit/redact/" + eventId)
                .with(httpBasic(WRITER, WRITER_PW))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fieldPaths\":[\"secret\"],\"reason\":\"test\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void writerCannotRunGlobalRetentionArchive() throws Exception {
        mockMvc.perform(post("/audit/retention/archive")
                .with(httpBasic(WRITER, WRITER_PW)))
            .andExpect(status().isForbidden());
    }

    @Test
    void readerCannotExportAnotherActorsAuditBundle() throws Exception {
        mockMvc.perform(get("/audit/export")
                .param("actorId", "other-actor")
                .with(httpBasic(READER, READER_PW)))
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
    void readerCanQueryRootAuditAlias() throws Exception {
        mockMvc.perform(get("/audit")
                .with(httpBasic(READER, READER_PW)))
            .andExpect(status().isOk());
    }

    @Test
    void readerCanQueryAuditSearchAlias() throws Exception {
        mockMvc.perform(get("/audit/search")
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
    void readerCanExportOwnAuditBundle() throws Exception {
        mockMvc.perform(get("/audit/export")
                .with(httpBasic(READER, READER_PW)))
            .andExpect(status().isOk());
    }

    @Test
    void writerCanCreateEvents() throws Exception {
        mockMvc.perform(post("/audit/events")
                .with(httpBasic(WRITER, WRITER_PW))
                .header("Idempotency-Key", "create-key-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEventBody()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.sequenceNumber").isNumber())
            .andExpect(jsonPath("$.data.contentHash").isString())
            .andExpect(jsonPath("$.data.chainHash").isString());
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

    @Test
    void adminCanRunGlobalRetentionArchive() throws Exception {
        mockMvc.perform(post("/audit/retention/archive")
                .with(httpBasic(ADMIN, ADMIN_PW)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archivedCount").isNumber());
    }

    // --- Replay / idempotency protection ---

    @Test
    void createEventWithoutIdempotencyKeyIsRejected() throws Exception {
        mockMvc.perform(post("/audit/events")
                .with(httpBasic(WRITER, WRITER_PW))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEventBody()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Idempotency-Key header is required for audit writes"));
    }

    @Test
    void duplicateIdempotencyKeyReturnsOriginalEventInsteadOfDuplicate() throws Exception {
        String body = "{\"eventType\":\"LOGIN\",\"actorId\":\"test-writer\",\"resourceType\":\"ACCOUNT\"," +
            "\"resourceId\":\"acct-replay\",\"payload\":{\"ok\":true}}";
        String idempotencyKey = "replay-key-" + UUID.randomUUID();

        String firstResponse = mockMvc.perform(post("/audit/events")
                .with(httpBasic(WRITER, WRITER_PW))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.replay").value(false))
            .andReturn().getResponse().getContentAsString();

        Long firstId = objectMapper.readTree(firstResponse).path("data").path("id").asLong();

        mockMvc.perform(post("/audit/events")
                .with(httpBasic(WRITER, WRITER_PW))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.replay").value(true))
            .andExpect(jsonPath("$.data.id").value(firstId));
    }

    @Test
    void writerCannotCreateEventForDifferentActorIdentity() throws Exception {
        String body = "{\"eventType\":\"LOGIN\",\"actorId\":\"other-actor\",\"resourceType\":\"ACCOUNT\"," +
            "\"resourceId\":\"acct-other\",\"payload\":{\"ok\":true}}";

        mockMvc.perform(post("/audit/events")
                .with(httpBasic(WRITER, WRITER_PW))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    void readerCannotQueryAnotherActorsEvents() throws Exception {
        mockMvc.perform(get("/audit/events")
                .param("actorId", "other-actor")
                .with(httpBasic(READER, READER_PW)))
            .andExpect(status().isForbidden());
    }

    // Rate limiting itself is unit-tested in isolation in RateLimitingFilterTest;
    // this shared Spring context serves many tests with the same credentials, so
    // exercising the limiter here would make quotas flaky across the whole suite.
}
