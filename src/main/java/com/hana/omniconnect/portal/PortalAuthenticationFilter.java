package com.hana.omniconnect.portal;

import java.io.IOException;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.hana.omniconnect.common.api.ApiResponse;
import com.hana.omniconnect.common.exception.ErrorCode;

@Component
public class PortalAuthenticationFilter extends OncePerRequestFilter {

    private final PortalTokenService tokenService;
    private final PortalUserRepository userRepository;
    private final PortalSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    public PortalAuthenticationFilter(
            PortalTokenService tokenService,
            PortalUserRepository userRepository,
            PortalSessionRepository sessionRepository,
            ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
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
        PortalUser user = principal == null ? null : userRepository.findByUserId(principal.userId()).orElse(null);
        if (user == null
                || user.role() != principal.role()
                || user.sessionVersion() != principal.sessionVersion()
                || !sessionRepository.isActive(
                        principal.sessionId(), principal.userId(), principal.sessionVersion(), Instant.now())) {
            writeError(response, ErrorCode.PORTAL_AUTHENTICATION_REQUIRED);
            return;
        }
        if (user.passwordChangeRequired() && !path.equals("/api/v1/portal/me") && !path.equals("/api/v1/portal/me/password")) {
            writeError(response, ErrorCode.PORTAL_PASSWORD_CHANGE_REQUIRED);
            return;
        }
        request.setAttribute(PortalAuthentication.USER_ID_ATTRIBUTE, principal.userId());
        request.setAttribute(PortalAuthentication.ROLE_ATTRIBUTE, principal.role());
        request.setAttribute(PortalAuthentication.SESSION_ID_ATTRIBUTE, principal.sessionId());
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal.userId(), null, java.util.List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(
                errorCode.status().value(), errorCode.code(), errorCode.message()));
    }
}
