package com.springda.devnest.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI devNestOpenApi() {
        var bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");
        return new OpenAPI()
                .info(new Info()
                        .title("DevNest REST API")
                        .version("v1")
                        .description("DevNest Web 与 Android 客户端共享的后端契约。"))
                .components(new Components().addSecuritySchemes("bearerAuth", bearerScheme))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    OpenApiCustomizer publicEndpointSecurity() {
        var publicAuthPaths = Set.of(
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "/api/v1/auth/display-name-availability"
        );
        return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
            if (publicAuthPaths.contains(path) || path.startsWith("/api/v1/public/")) {
                pathItem.readOperations().forEach(operation -> operation.setSecurity(List.of()));
            }
        });
    }
}
