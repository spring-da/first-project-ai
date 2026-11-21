package com.springda.devnest.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-ID";
    public static final String ATTRIBUTE = RequestCorrelationFilter.class.getName() + ".requestId";

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestCorrelationFilter.class);
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final long SLOW_REQUEST_MILLIS = 2_000;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var requestId = resolveRequestId(request.getHeader(HEADER));
        var startedAt = System.nanoTime();
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);

        try (var ignored = MDC.putCloseable("requestId", requestId)) {
            try {
                filterChain.doFilter(request, response);
            } finally {
                var elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
                logCompletion(request, response.getStatus(), elapsedMillis);
            }
        }
    }

    public static String requestId(HttpServletRequest request) {
        var value = request.getAttribute(ATTRIBUTE);
        return value instanceof String id && !id.isBlank() ? id : "unavailable";
    }

    private String resolveRequestId(String candidate) {
        if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) return candidate;
        return UUID.randomUUID().toString();
    }

    private void logCompletion(HttpServletRequest request, int status, long elapsedMillis) {
        if (status >= 500) {
            LOGGER.error("HTTP {} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), status, elapsedMillis);
        } else if (status >= 400 || elapsedMillis >= SLOW_REQUEST_MILLIS) {
            LOGGER.warn("HTTP {} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), status, elapsedMillis);
        } else {
            LOGGER.debug("HTTP {} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), status, elapsedMillis);
        }
    }
}
