package com.hana.omniconnect.config;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-HANA-OMNI-CONNECT-CORRELATION-ID";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._:-]{8,128}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request.getHeader(HEADER_NAME));
        response.setHeader(HEADER_NAME, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveCorrelationId(String providedCorrelationId) {
        if (StringUtils.hasText(providedCorrelationId)
                && SAFE_CORRELATION_ID.matcher(providedCorrelationId).matches()) {
            return providedCorrelationId;
        }
        return UUID.randomUUID().toString();
    }
}
