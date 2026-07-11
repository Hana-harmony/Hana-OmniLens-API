package com.hana.omnilens.config;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.hana.omnilens.security.SecurityAuditLogger;

@Component
public class MtlsClientCertificateFilter extends OncePerRequestFilter {

    static final String CLIENT_CERTIFICATE_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";

    private final MtlsProperties properties;
    private final SecurityAuditLogger securityAuditLogger;

    public MtlsClientCertificateFilter(MtlsProperties properties, SecurityAuditLogger securityAuditLogger) {
        this.properties = properties;
        this.securityAuditLogger = securityAuditLogger;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.enabled() || isPublicEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        X509Certificate[] certificates = clientCertificates(request);
        if (certificates.length == 0) {
            securityAuditLogger.record(request, "failure", "", "client_certificate_missing");
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Client certificate is required");
            return;
        }

        try {
            certificates[0].checkValidity();
        } catch (CertificateException exception) {
            securityAuditLogger.record(request, "failure", "", "client_certificate_invalid");
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Client certificate is invalid");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private X509Certificate[] clientCertificates(HttpServletRequest request) {
        Object attribute = request.getAttribute(CLIENT_CERTIFICATE_ATTRIBUTE);
        if (attribute instanceof X509Certificate[] certificates) {
            return certificates;
        }
        return new X509Certificate[0];
    }

    private boolean isPublicEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health")
                || path.equals("/actuator/info")
                || path.startsWith("/api/v1/portal/");
    }
}
