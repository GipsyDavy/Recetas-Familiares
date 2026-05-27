package org.gipsybuho.recetasfamiliares.common.config;

import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI recetasFamiliaresOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Recetas Familiares API")
                        .version("v1")
                        .description("API cliente-servidor para Recetas Familiares"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    @Bean
    OpenApiCustomizer recetasFamiliaresSecurityOpenApiCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) ->
                pathItem.readOperations().forEach(operation -> {
                    if (path.startsWith("/api/v1/auth") || path.startsWith("/api/v1/health")) {
                        operation.setSecurity(List.of());
                    } else if (path.startsWith("/api/v1/")) {
                        operation.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
                    }
                })
        );
    }
}
