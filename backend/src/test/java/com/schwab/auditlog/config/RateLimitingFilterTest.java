package com.schwab.auditlog.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Isolated unit test for the sliding-window limiter (no Spring context), so its
 * behavior doesn't depend on - or get skewed by - request volume from other test classes
 * sharing a cached application context.
 */
class RateLimitingFilterTest {

    @Test
    void allowsRequestsUnderTheLimitThenBlocksWith429() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(3, 60);

        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/audit/events");
            request.addHeader("Authorization", "Basic sameclient");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertNotEquals(429, response.getStatus(), "Request " + i + " should not be throttled yet");
        }

        MockHttpServletRequest blockedRequest = new MockHttpServletRequest("GET", "/audit/events");
        blockedRequest.addHeader("Authorization", "Basic sameclient");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilter(blockedRequest, blockedResponse, new MockFilterChain());

        assertEquals(429, blockedResponse.getStatus());
        assertNotNull(blockedResponse.getHeader("Retry-After"));
    }

    @Test
    void differentClientsHaveIndependentQuotas() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(1, 60);

        MockHttpServletRequest clientA = new MockHttpServletRequest("GET", "/audit/events");
        clientA.addHeader("Authorization", "Basic client-a");
        MockHttpServletResponse responseA = new MockHttpServletResponse();
        filter.doFilter(clientA, responseA, new MockFilterChain());
        assertNotEquals(429, responseA.getStatus());

        MockHttpServletRequest clientB = new MockHttpServletRequest("GET", "/audit/events");
        clientB.addHeader("Authorization", "Basic client-b");
        MockHttpServletResponse responseB = new MockHttpServletResponse();
        filter.doFilter(clientB, responseB, new MockFilterChain());
        assertNotEquals(429, responseB.getStatus(), "A different client's quota must be independent");
    }

    @Test
    void healthAndSwaggerEndpointsAreNeverThrottled() {
        RateLimitingFilter filter = new RateLimitingFilter(1, 60);
        MockHttpServletRequest health = new MockHttpServletRequest("GET", "/audit/health");
        assertTrue(filter.shouldNotFilter(health));

        MockHttpServletRequest swagger = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        assertTrue(filter.shouldNotFilter(swagger));
    }
}
