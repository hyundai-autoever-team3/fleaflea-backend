package com.anabada.fleaflea.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.List;

@Configuration
public class SwaggerConfig {

    private static final String ACCESS_TOKEN_SCHEME = "accessToken";

    @Bean
    public OpenAPI openAPI(
            @Value("${swagger.server-url}") String serverUrl
    ) {
        return new OpenAPI()
                .info(new Info()
                        .title("FleaFlea API")
                        .description("FleaFlea API 문서")
                        .version("v1"))
                .addServersItem(new Server().url(serverUrl))
                .components(new Components()
                        .addSecuritySchemes(ACCESS_TOKEN_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(ACCESS_TOKEN_SCHEME));
    }

    @Bean
    public OpenApiCustomizer publicAuthOperations() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            for (String path : List.of(
                    "/api/v1/auth/signup",
                    "/api/v1/auth/login",
                    "/api/v1/auth/reissue",
                    "/api/v1/auth/oauth2/signup"
            )) {
                var item = openApi.getPaths().get(path);
                if (item != null && item.getPost() != null) {
                    item.getPost().setSecurity(List.of());
                }
            }
        };
    }
}
