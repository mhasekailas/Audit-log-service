package com.schwab.auditlog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.config.http.SessionCreationPolicy;

/**
 * Authorization matrix (least privilege, roles are additive - ADMIN implies WRITER implies READER):
 *  - AUDIT_READER : read-only access (query/verify/export/payload/stats)
 *  - AUDIT_WRITER : READER + can create events, redact payloads, run retention archival
 *  - AUDIT_ADMIN  : WRITER + retention policy configuration and compliance-access reporting
 */
@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http, RateLimitingFilter rateLimitingFilter, RequestSizeLimitFilter requestSizeLimitFilter
    ) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/audit/health", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Compliance access logging/reporting is admin-only (contains sensitive account access records)
                .requestMatchers("/audit/compliance/**", "/audit/compliance-report").hasRole("AUDIT_ADMIN")
                // Retention policy configuration is administrative; running archival is an operational write
                .requestMatchers(HttpMethod.POST, "/audit/retention-policies").hasRole("AUDIT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/audit/retention/archive").hasAnyRole("AUDIT_WRITER", "AUDIT_ADMIN")
                // Mutating audit endpoints require writer (or admin) role
                .requestMatchers(HttpMethod.POST, "/audit/events", "/audit/events/*/redact")
                    .hasAnyRole("AUDIT_WRITER", "AUDIT_ADMIN")
                // Read-only audit endpoints are available to any authenticated audit role
                .requestMatchers(HttpMethod.GET, "/audit/**")
                    .hasAnyRole("AUDIT_READER", "AUDIT_WRITER", "AUDIT_ADMIN")
                .anyRequest().authenticated())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(Customizer.withDefaults())
            .addFilterBefore(requestSizeLimitFilter, BasicAuthenticationFilter.class)
            .addFilterBefore(rateLimitingFilter, BasicAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(
        @Value("${AUDIT_API_USERNAME}") String writerUsername,
        @Value("${AUDIT_API_PASSWORD}") String writerPassword,
        @Value("${AUDIT_READER_USERNAME}") String readerUsername,
        @Value("${AUDIT_READER_PASSWORD}") String readerPassword,
        @Value("${AUDIT_ADMIN_USERNAME}") String adminUsername,
        @Value("${AUDIT_ADMIN_PASSWORD}") String adminPassword,
        PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(
            User.withUsername(readerUsername)
                .password(passwordEncoder.encode(readerPassword))
                .roles("AUDIT_READER")
                .build(),
            User.withUsername(writerUsername)
                .password(passwordEncoder.encode(writerPassword))
                .roles("AUDIT_READER", "AUDIT_WRITER")
                .build(),
            User.withUsername(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .roles("AUDIT_READER", "AUDIT_WRITER", "AUDIT_ADMIN")
                .build());
    }
}

