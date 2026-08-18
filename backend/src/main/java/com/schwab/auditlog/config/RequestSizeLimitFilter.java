package com.schwab.auditlog.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects requests whose declared Content-Length exceeds the configured limit before any
 * body parsing/DB work happens - a basic defense against oversized-payload abuse/DoS.
 * Clients that omit Content-Length (chunked transfer) are still bounded by Tomcat's
 * server.max-http-request-header-size/connector limits.
 */
@Component
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBodyBytes;

    public RequestSizeLimitFilter(@Value("${audit.security.request.max-body-bytes:1048576}") long maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBodyBytes) {
            response.setStatus(413);
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"success\":false,\"error\":\"Request payload exceeds the maximum allowed size\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
