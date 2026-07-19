package com.hana.omniconnect.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String API_KEY_SCHEME = "hanaApiKey";

    @Bean
    OpenAPI hanaOmniConnectOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hana Omni-Connect API")
                        .version("v1")
                        .description("B2B API for Korean stock quotes, orderability, and news/disclosure intelligence."))
                .components(new Components()
                        .addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-HANA-OMNI-CONNECT-API-KEY")))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
    }
}
