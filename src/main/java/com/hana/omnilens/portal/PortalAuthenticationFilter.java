package com.hana.omnilens.portal;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.hana.omnilens.common.api.ApiResponse;
import com.hana.omnilens.common.exception.ErrorCode;

@Component
public class PortalAuthenticationFilter extends OncePerRequestFilter {

    private final PortalTokenService tokenService;
    private final ObjectMapper objectMapper;

    public PortalAuthenticationFilter(PortalTokenService tokenService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/portal/") || path.startsWith("/api/v1/portal/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }
        String authorization = request.getHeader("Authorization");
        String token = StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length()).trim()
                : "";
        PortalTokenService.PortalPrincipal principal = tokenService.verify(token);
        if (principal == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ApiResponse.error(
                    ErrorCode.PORTAL_AUTHENTICATION_REQUIRED.status().value(),
                    ErrorCode.PORTAL_AUTHENTICATION_REQUIRED.code(),
                    ErrorCode.PORTAL_AUTHENTICATION_REQUIRED.message()));
            return;
        }
        request.setAttribute(PortalAuthentication.USER_ID_ATTRIBUTE, principal.userId());
        request.setAttribute(PortalAuthentication.ROLE_ATTRIBUTE, principal.role());
        filterChain.doFilter(request, response);
    }
}
