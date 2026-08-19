package com.schwab.auditlog.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class HttpsEnforcementFilterTest {

    @Test
    void disabledFilterAllowsHttpRequests() throws Exception {
        HttpsEnforcementFilter filter = new HttpsEnforcementFilter(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/audit/events");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNotEquals(403, response.getStatus());
    }

    @Test
    void requiredFilterRejectsPlainHttpRequests() throws Exception {
        HttpsEnforcementFilter filter = new HttpsEnforcementFilter(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/audit/events");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("HTTPS is required"));
    }

    @Test
    void requiredFilterAllowsDirectHttpsRequests() throws Exception {
        HttpsEnforcementFilter filter = new HttpsEnforcementFilter(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/audit/events");
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNotEquals(403, response.getStatus());
    }

    @Test
    void requiredFilterAllowsTlsTerminatedProxyRequests() throws Exception {
        HttpsEnforcementFilter filter = new HttpsEnforcementFilter(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/audit/events");
        request.addHeader("X-Forwarded-Proto", "https");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNotEquals(403, response.getStatus());
    }

    @Test
    void healthEndpointIsAllowedForLoadBalancers() {
        HttpsEnforcementFilter filter = new HttpsEnforcementFilter(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/audit/health");

        assertTrue(filter.shouldNotFilter(request));
    }
}