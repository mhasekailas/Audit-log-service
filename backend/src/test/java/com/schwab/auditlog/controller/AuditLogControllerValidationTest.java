package com.schwab.auditlog.controller;

import com.schwab.auditlog.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Malformed/boundary request matrix and DB-failure handling for the audit API.
 * Complements AuditLogControllerSecurityTest (auth/role matrix).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditLogControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditEventRepository auditEventRepository;

    private static final String WRITER = "test-writer";
    private static final String WRITER_PW = "writer-pass-123";

    @Test
    void missingRequiredFieldReturns400WithFieldErrors() throws Exception {
        String body = "{\"eventType\":\"LOGIN\",\"actorId\":\"actor-1\"}"; // missing resourceType/resourceId/payload

        mockMvc.perform(post("/audit/events")
                .with(httpBasic(WRITER, WRITER_PW))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fieldErrors.resourceType").exists())
            .andExpect(jsonPath("$.fieldErrors.resourceId").exists())
            .andExpect(jsonPath("$.fieldErrors.payload").exists());
    }

    @Test
    void fieldExceedingMaxLengthReturns400() throws Exception {
        String oversizedEventType = "X".repeat(51);
        String body = "{\"eventType\":\"" + oversizedEventType + "\",\"actorId\":\"actor-1\"," +
            "\"resourceType\":\"ACCOUNT\",\"resourceId\":\"acct-1\",\"payload\":{\"ok\":true}}";

        mockMvc.perform(post("/audit/events")
                .with(httpBasic(WRITER, WRITER_PW))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.eventType").exists());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/audit/events")
                .with(httpBasic(WRITER, WRITER_PW))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ this is not valid json "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void oversizedPayloadReturns413() throws Exception {
        // test properties cap audit.security.request.max-body-bytes at 2048
        String hugePayload = "{\"eventType\":\"LOGIN\",\"actorId\":\"actor-1\",\"resourceType\":\"ACCOUNT\"," +
            "\"resourceId\":\"acct-1\",\"payload\":{\"blob\":\"" + "A".repeat(4000) + "\"}}";

        mockMvc.perform(post("/audit/events")
                .with(httpBasic(WRITER, WRITER_PW))
                .contentType(MediaType.APPLICATION_JSON)
                .content(hugePayload))
            .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void nonNumericPathVariableReturns400() throws Exception {
        mockMvc.perform(get("/audit/events/not-a-number")
                .with(httpBasic(WRITER, WRITER_PW)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void negativePageReturns400() throws Exception {
        mockMvc.perform(get("/audit/events")
                .param("page", "-1")
                .with(httpBasic(WRITER, WRITER_PW)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void excessivePageLimitReturns400() throws Exception {
        mockMvc.perform(get("/audit/events")
                .param("limit", "201")
                .with(httpBasic(WRITER, WRITER_PW)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void databaseFailureDuringQueryReturnsGenericServerErrorWithoutLeakingDetails() throws Exception {
        when(auditEventRepository.findAllByOrderBySequenceNumberAsc())
            .thenThrow(new DataAccessResourceFailureException("Connection refused: secret-jdbc-url-detail"));

        mockMvc.perform(get("/audit/verify").with(httpBasic(WRITER, WRITER_PW)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error").value("A server error occurred. Please try again later."))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("secret-jdbc-url-detail"))));
    }
}
