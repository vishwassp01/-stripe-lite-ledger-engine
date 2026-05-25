package com.vishwas.ledger.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishwas.ledger.dto.ErrorResponse;
import com.vishwas.ledger.exception.IdempotencyException;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class IdempotencyFilter implements Filter {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String REDIS_PREFIX = "idemp:";
    private static final Duration KEY_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyFilter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Apply idempotency check only to POST transfer requests
        if ("POST".equalsIgnoreCase(httpRequest.getMethod()) && "/api/ledger/transfer".equals(httpRequest.getRequestURI())) {
            String keyHeader = httpRequest.getHeader(IDEMPOTENCY_HEADER);

            // 1. Enforce presence of Idempotency-Key header
            if (keyHeader == null || keyHeader.trim().isEmpty()) {
                sendErrorResponse(httpResponse, HttpStatus.BAD_REQUEST, "Idempotency-Key HTTP header is required for transfers");
                return;
            }

            String redisKey = REDIS_PREFIX + keyHeader.trim();

            // 2. Check if this idempotency key is already cached in Redis
            // Use setIfAbsent atomically to prevent race conditions on double clicks
            Boolean isNewKey = redisTemplate.opsForValue().setIfAbsent(redisKey, "IN_PROGRESS", KEY_TTL);

            if (Boolean.FALSE.equals(isNewKey)) {
                // Key already exists. Check the state.
                String cachedValue = redisTemplate.opsForValue().get(redisKey);

                if ("IN_PROGRESS".equals(cachedValue)) {
                    // Transaction is currently being processed by another thread/request. Return 409 Conflict.
                    sendErrorResponse(httpResponse, HttpStatus.CONFLICT, "A transaction with this Idempotency-Key is already in progress");
                    return;
                } else if (cachedValue != null && cachedValue.startsWith("COMPLETED:")) {
                    // Transaction has completed. Replay the exact cached response body and status.
                    String[] parts = cachedValue.split(":", 3);
                    int statusCode = Integer.parseInt(parts[1]);
                    String cachedBody = parts[2];

                    httpResponse.setStatus(statusCode);
                    httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    httpResponse.getWriter().write(cachedBody);
                    return;
                } else {
                    // Fallback / Corrupt state in cache. Reset and allow reprocessing.
                    redisTemplate.opsForValue().set(redisKey, "IN_PROGRESS", KEY_TTL);
                }
            }

            // 3. Key is new. Execute request and cache the output.
            ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(httpResponse);
            boolean success = false;

            try {
                chain.doFilter(httpRequest, responseWrapper);
                success = true;

                byte[] responseArray = responseWrapper.getContentAsByteArray();
                String responseBody = new String(responseArray, responseWrapper.getCharacterEncoding());
                int status = responseWrapper.getStatus();

                // Only cache successful (2xx) or client errors (4xx).
                // Do not cache transient server errors (5xx) so they can be retried.
                if (status >= 200 && status < 500) {
                    redisTemplate.opsForValue().set(redisKey, "COMPLETED:" + status + ":" + responseBody, KEY_TTL);
                } else {
                    // Evict from Redis so client can retry immediately
                    redisTemplate.delete(redisKey);
                }

                responseWrapper.copyBodyToResponse();
            } finally {
                if (!success) {
                    // An unhandled exception or system failure occurred. Evict lock so request can be retried.
                    redisTemplate.delete(redisKey);
                }
            }
        } else {
            // Proceed normally for other routes (like GET balances or history)
            chain.doFilter(request, response);
        }
    }

    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .build();

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
