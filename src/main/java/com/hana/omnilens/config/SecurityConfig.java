package com.hana.omnilens.config;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hana.omnilens.common.api.ApiResponse;
import com.hana.omnilens.common.exception.ErrorCode;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.hana.omnilens.portal.PortalAuthenticationFilter;
import com.hana.omnilens.portal.PortalProperties;

@Configuration
@EnableConfigurationProperties({
        OmniLensSecurityProperties.class,
        ApiRateLimitProperties.class,
        ApiSignatureProperties.class,
        PortalProperties.class
})
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiKeyAuthenticationFilter apiKeyFilter,
            PortalAuthenticationFilter portalAuthenticationFilter,
            ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writePortalError(
                                response, objectMapper, ErrorCode.PORTAL_AUTHENTICATION_REQUIRED))
                        .accessDeniedHandler((request, response, exception) -> writePortalError(
                                response, objectMapper, ErrorCode.PORTAL_ACCESS_DENIED)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/portal/auth/**").permitAll()
                        .requestMatchers("/api/v1/portal/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/portal/**").authenticated()
                        .anyRequest().permitAll())
				.addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(portalAuthenticationFilter, ApiKeyAuthenticationFilter.class)
                .build();
    }

    private void writePortalError(
            jakarta.servlet.http.HttpServletResponse response,
            ObjectMapper objectMapper,
            ErrorCode errorCode) throws java.io.IOException {
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(
                errorCode.status().value(), errorCode.code(), errorCode.message()));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(OmniLensSecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.corsAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "X-HANA-OMNILENS-API-KEY",
                "X-HANA-OMNILENS-TIMESTAMP",
                "X-HANA-OMNILENS-NONCE",
                "X-HANA-OMNILENS-SIGNATURE",
                "X-HANA-OMNILENS-CORRELATION-ID",
                "Authorization",
                "Content-Type"));
        configuration.setExposedHeaders(List.of("X-HANA-OMNILENS-CORRELATION-ID"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
