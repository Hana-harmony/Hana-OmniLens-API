package com.hana.omniconnect.config;

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
import com.hana.omniconnect.common.api.ApiResponse;
import com.hana.omniconnect.common.exception.ErrorCode;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.hana.omniconnect.portal.PortalAuthenticationFilter;
import com.hana.omniconnect.portal.PortalProperties;

@Configuration
@EnableConfigurationProperties({
        OmniConnectSecurityProperties.class,
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
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
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
    CorsConfigurationSource corsConfigurationSource(OmniConnectSecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.corsAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "X-HANA-OMNI-CONNECT-API-KEY",
                "X-HANA-OMNI-CONNECT-TIMESTAMP",
                "X-HANA-OMNI-CONNECT-NONCE",
                "X-HANA-OMNI-CONNECT-SIGNATURE",
                "X-HANA-OMNI-CONNECT-CORRELATION-ID",
                "Authorization",
                "Content-Type"));
        configuration.setExposedHeaders(List.of("X-HANA-OMNI-CONNECT-CORRELATION-ID"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
