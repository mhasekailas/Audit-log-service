package com.schwab.auditlog.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Authorization matrix (least privilege, roles are additive - ADMIN implies WRITER implies READER):
 *  - AUDIT_READER : read-only access (query/verify/export/payload/stats)
 *  - AUDIT_WRITER : READER + can create events, redact payloads, run retention archival
 *  - AUDIT_ADMIN  : WRITER + retention policy configuration and compliance-access reporting
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        HttpsEnforcementFilter httpsEnforcementFilter,
        RateLimitingFilter rateLimitingFilter,
        RequestSizeLimitFilter requestSizeLimitFilter,
        @Value("${audit.security.basic.enabled:true}") boolean basicEnabled,
        @Value("${audit.security.oauth2.enabled:false}") boolean oauth2Enabled
    ) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required"))
                .accessDeniedHandler((request, response, accessDeniedException) -> writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Access denied for the requested actor identity")))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/audit/health", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Compliance access logging/reporting is admin-only (contains sensitive account access records)
                .requestMatchers("/audit/compliance/**", "/audit/compliance-report").hasRole("AUDIT_ADMIN")
                // Lock down all audit write paths by default; only explicitly approved mutation endpoints may pass.
                .requestMatchers(HttpMethod.POST, "/audit").denyAll()
                .requestMatchers(HttpMethod.POST, "/audit/retention-policies").hasRole("AUDIT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/audit/retention/archive", "/audit/archive").hasRole("AUDIT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/audit/events", "/audit/events/*/redact", "/audit/redact/**").hasAnyRole("AUDIT_WRITER", "AUDIT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/audit/**").denyAll()
                // Root and search audit routes are broad read surfaces and must not be public.
                .requestMatchers(HttpMethod.GET, "/audit", "/audit/search")
                    .hasAnyRole("AUDIT_READER", "AUDIT_WRITER", "AUDIT_ADMIN")
                .requestMatchers(HttpMethod.GET, "/audit/verify", "/audit/verify/**", "/audit/export",
                    "/audit/*/payload", "/audit/*/stats", "/audit/merkle/**")
                    .hasAnyRole("AUDIT_READER", "AUDIT_WRITER", "AUDIT_ADMIN")
                // Read-only audit endpoints are available to any authenticated audit role
                .requestMatchers(HttpMethod.GET, "/audit/**")
                    .hasAnyRole("AUDIT_READER", "AUDIT_WRITER", "AUDIT_ADMIN")
                .anyRequest().authenticated())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(httpsEnforcementFilter, BasicAuthenticationFilter.class)
            .addFilterBefore(requestSizeLimitFilter, BasicAuthenticationFilter.class)
            .addFilterBefore(rateLimitingFilter, BasicAuthenticationFilter.class);
        if (basicEnabled) {
            http.httpBasic(Customizer.withDefaults());
        } else {
            http.httpBasic(AbstractHttpConfigurer::disable);
        }
        if (oauth2Enabled) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        }
        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::jwtAuthorities);
        return converter;
    }

    private Collection<GrantedAuthority> jwtAuthorities(Jwt jwt) {
        Object roles = jwt.getClaims().getOrDefault("roles", List.of());
        if (!(roles instanceof Collection<?> roleCollection)) {
            return List.of();
        }
        return roleCollection.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .filter(role -> role.equals("AUDIT_READER") || role.equals("AUDIT_WRITER") || role.equals("AUDIT_ADMIN"))
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .collect(Collectors.toSet());
    }

    private void writeJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":false,\"error\":\"" + message + "\"}");
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
        @Value("${audit.security.cors.allowed-origins:}") String allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
            configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
            configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
            configuration.setExposedHeaders(List.of("Retry-After"));
            configuration.setAllowCredentials(true);
            configuration.setMaxAge(3600L);
        }
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(
        @Value("${AUDIT_API_USERNAME}") String writerUsername,
        @Value("${AUDIT_API_PASSWORD}") String writerPassword,
        @Value("${AUDIT_API_ENABLED:true}") boolean writerEnabled,
        @Value("${AUDIT_API_EXPIRES_AT:}") String writerExpiresAt,
        @Value("${AUDIT_READER_USERNAME}") String readerUsername,
        @Value("${AUDIT_READER_PASSWORD}") String readerPassword,
        @Value("${AUDIT_READER_ENABLED:true}") boolean readerEnabled,
        @Value("${AUDIT_READER_EXPIRES_AT:}") String readerExpiresAt,
        @Value("${AUDIT_ADMIN_USERNAME}") String adminUsername,
        @Value("${AUDIT_ADMIN_PASSWORD}") String adminPassword,
        @Value("${AUDIT_ADMIN_ENABLED:true}") boolean adminEnabled,
        @Value("${AUDIT_ADMIN_EXPIRES_AT:}") String adminExpiresAt,
        PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(
            User.withUsername(readerUsername)
                .password(passwordEncoder.encode(readerPassword))
                .disabled(!readerEnabled)
                .accountExpired(isExpired(readerExpiresAt))
                .roles("AUDIT_READER")
                .build(),
            User.withUsername(writerUsername)
                .password(passwordEncoder.encode(writerPassword))
                .disabled(!writerEnabled)
                .accountExpired(isExpired(writerExpiresAt))
                .roles("AUDIT_READER", "AUDIT_WRITER")
                .build(),
            User.withUsername(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .disabled(!adminEnabled)
                .accountExpired(isExpired(adminExpiresAt))
                .roles("AUDIT_READER", "AUDIT_WRITER", "AUDIT_ADMIN")
                .build());
    }

    private boolean isExpired(String expiresAt) {
        if (expiresAt == null || expiresAt.isBlank()) {
            return false;
        }
        try {
            return !OffsetDateTime.parse(expiresAt).isAfter(OffsetDateTime.now());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Account expiry must be ISO-8601 offset date-time", e);
        }
    }
}

