package com.schwab.auditlog.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Simple in-memory sliding-window rate limiter for the /audit API surface.
 * Mitigates brute-force credential guessing and abusive/replayed request bursts.
 * Keyed by the caller's Authorization header (falls back to remote address) so one
 * noisy client cannot exhaust another client's quota.
 *
 * Note: state is per-instance; a multi-node deployment needs a shared store (e.g. Redis)
 * for a globally consistent limit.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final int maxRequests;
    private final Duration window;
    private final ConcurrentHashMap<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();

    public RateLimitingFilter(
        @Value("${audit.security.rate-limit.max-requests:60}") int maxRequests,
        @Value("${audit.security.rate-limit.window-seconds:60}") long windowSeconds
    ) {
        this.maxRequests = maxRequests;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.contains("/audit/health") || path.contains("/swagger-ui") || path.contains("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String key = clientKey(request);
        long now = System.currentTimeMillis();
        long windowStartMillis = now - window.toMillis();
        Deque<Long> timestamps = requestLog.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        boolean allowed;
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStartMillis) {
                timestamps.pollFirst();
            }
            allowed = timestamps.size() < maxRequests;
            if (allowed) {
                timestamps.addLast(now);
            }
        }

        if (!allowed) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(window.getSeconds()));
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"error\":\"Rate limit exceeded. Try again later.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        return auth != null ? auth : String.valueOf(request.getRemoteAddr());
    }
}
