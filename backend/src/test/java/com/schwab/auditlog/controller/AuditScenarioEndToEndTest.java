package com.schwab.auditlog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end walk-through of every business scenario through the real HTTP layer
 * (MockMvc + full Spring context + H2), exercising the full request/response contract
 * for each API rather than mocking the service layer:
 *
 *  - Scenario A: append event, query, verify chain, fetch by id
 *  - Scenario B: redact a payload field, configure + run retention archival, bulk export
 *  - Scenario C: record a compliance access decision and generate a compliance report
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuditScenarioEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String WRITER = "test-writer";
    private static final String WRITER_PW = "writer-pass-123";
    private static final String READER = "test-reader";
    private static final String READER_PW = "reader-pass-123";
    private static final String ADMIN = "test-admin";
    private static final String ADMIN_PW = "admin-pass-123";

    private static Long createdEventId;

    @Test
    @Order(1)
    void scenarioA_createEvent() throws Exception {
        String body = "{\"eventType\":\"LOGIN\",\"actorId\":\"e2e-actor\",\"resourceType\":\"ACCOUNT\"," +
            "\"resourceId\":\"e2e-acct\",\"payload\":{\"ssn\":\"123-45-6789\",\"note\":\"ok\"}}";

        String response = mockMvc.perform(post("/audit/events")
                .with(httpBasic(WRITER, WRITER_PW))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.chainHash").exists())
            .andReturn().getResponse().getContentAsString();

        createdEventId = objectMapper.readTree(response).path("data").path("id").asLong();
    }

    @Test
    @Order(2)
    void scenarioA_queryEventsFindsCreatedEvent() throws Exception {
        mockMvc.perform(get("/audit/events")
                .param("actorId", "e2e-actor")
                .with(httpBasic(READER, READER_PW)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.totalCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(3)
    void scenarioA_getEventById() throws Exception {
        mockMvc.perform(get("/audit/events/" + createdEventId)
                .with(httpBasic(READER, READER_PW)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(createdEventId));
    }

    @Test
    @Order(4)
    void scenarioA_verifyChainIsValid() throws Exception {
        mockMvc.perform(get("/audit/verify").with(httpBasic(READER, READER_PW)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.isValid").value(true));
    }

    @Test
    @Order(5)
    void scenarioB_redactPreservesChainIntegrity() throws Exception {
        mockMvc.perform(post("/audit/events/" + createdEventId + "/redact")
                .with(httpBasic(WRITER, WRITER_PW))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fieldPaths\":[\"ssn\"],\"reason\":\"PII cleanup\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.payload.ssn").value("[REDACTED]"));

        // Redaction rewrites hashes for every record; the chain must still verify afterward.
        mockMvc.perform(get("/audit/verify").with(httpBasic(READER, READER_PW)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.isValid").value(true));
    }

    @Test
    @Order(6)
    void scenarioB_retentionPolicyAndArchival() throws Exception {
        // Seed an old event that should be swept up by a 1-day retention policy.
        LocalDateTime oldTimestamp = LocalDateTime.now().minusDays(400);
        String oldEventBody = "{\"eventType\":\"LOGIN\",\"actorId\":\"e2e-archive-actor\"," +
            "\"resourceType\":\"RETENTION_TEST\",\"resourceId\":\"e2e-archive-acct\"," +
            "\"payload\":{\"ok\":true},\"timestamp\":\"" +
            oldTimestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\"}";

        mockMvc.perform(post("/audit/events")
                .with(httpBasic(WRITER, WRITER_PW))
                .contentType(MediaType.APPLICATION_JSON)
                .content(oldEventBody))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/audit/retention-policies")
                .with(httpBasic(ADMIN, ADMIN_PW))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resourceType\":\"RETENTION_TEST\",\"retentionDays\":1}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/audit/retention/archive")
                .with(httpBasic(WRITER, WRITER_PW)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archivedCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(7)
    void scenarioB_exportProducesVerifiableBundle() throws Exception {
        mockMvc.perform(get("/audit/export")
                .param("actorId", "e2e-actor")
                .with(httpBasic(READER, READER_PW)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.exportType").value("ACTOR_ID"))
            .andExpect(jsonPath("$.records[0].chainHash").exists());
    }

    @Test
    @Order(8)
    void scenarioC_recordComplianceAccessAndReport() throws Exception {
        String accessBody = "{\"auditEventId\":" + createdEventId + ",\"accessType\":\"READ\"," +
            "\"userRole\":\"ANALYST\",\"ipAddress\":\"127.0.0.1\",\"userAgent\":\"junit\"," +
            "\"accessResult\":\"SUCCESS\"}";

        mockMvc.perform(post("/audit/compliance/access")
                .with(httpBasic(ADMIN, ADMIN_PW))
                .contentType(MediaType.APPLICATION_JSON)
                .content(accessBody))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/audit/compliance-report").with(httpBasic(ADMIN, ADMIN_PW)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.records").isArray());
    }

    @Test
    @Order(9)
    void healthEndpointReportsUp() throws Exception {
        mockMvc.perform(get("/audit/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }
}
