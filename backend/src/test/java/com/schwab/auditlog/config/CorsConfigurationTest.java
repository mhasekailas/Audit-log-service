package com.schwab.auditlog.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.*;

class CorsConfigurationTest {

    @Test
    void corsIsDenyByDefaultWhenNoOriginsAreConfigured() {
        SecurityConfig securityConfig = new SecurityConfig();
        CorsConfigurationSource source = securityConfig.corsConfigurationSource("");

        CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/audit/events"));

        assertNotNull(configuration);
        assertTrue(configuration.getAllowedOrigins() == null || configuration.getAllowedOrigins().isEmpty());
    }

    @Test
    void corsAllowsOnlyConfiguredOriginsAndHeaders() {
        SecurityConfig securityConfig = new SecurityConfig();
        CorsConfigurationSource source = securityConfig.corsConfigurationSource(
            "https://audit.example.com, https://admin.example.com");

        CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/audit/events"));

        assertNotNull(configuration);
        assertEquals(2, configuration.getAllowedOrigins().size());
        assertTrue(configuration.getAllowedOrigins().contains("https://audit.example.com"));
        assertTrue(configuration.getAllowedOrigins().contains("https://admin.example.com"));
        assertTrue(configuration.getAllowedHeaders().contains("Idempotency-Key"));
        assertTrue(configuration.getAllowedMethods().contains("POST"));
        assertTrue(Boolean.TRUE.equals(configuration.getAllowCredentials()));
    }
}