package com.tickethub.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.common.security.RateLimiter.Decision;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;

/**
 * Захист від брутфорсу пароля (ліміт на IP) і ботів-скальперів (ліміт на користувача).
 * Стоїть після JwtAuthFilter, тож для /orders уже відомо, хто робить запит.
 * Не є Spring-біном з тієї ж причини, що й JwtAuthFilter.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiter rateLimiter, RateLimitProperties properties, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || !HttpMethod.POST.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var path = request.getRequestURI();
        Decision decision = null;
        if (path.equals("/api/v1/auth/login")) {
            // за проксі/балансувальником потрібен server.forward-headers-strategy, інакше всі матимуть IP проксі
            decision = rateLimiter.tryConsume("login:" + request.getRemoteAddr(), properties.login());
        } else if (path.equals("/api/v1/orders")
                && SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof CurrentUser user) {
            decision = rateLimiter.tryConsume("orders:" + user.id(), properties.orders());
        }

        switch (decision) {
            case Decision.Rejected rejected -> reject(request, response, rejected);
            case Decision.Allowed allowed -> {
                response.setHeader("X-RateLimit-Remaining", String.valueOf(allowed.remaining()));
                chain.doFilter(request, response);
            }
            case Decision.Unchecked unchecked -> chain.doFilter(request, response);
            case null -> chain.doFilter(request, response);
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, Decision.Rejected rejected)
            throws IOException {
        long retryAfterSeconds = Math.max(1, (long) Math.ceil(rejected.retryAfter().toMillis() / 1000.0));
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests, retry in %d s".formatted(retryAfterSeconds));
        problem.setTitle("Too Many Requests");
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
