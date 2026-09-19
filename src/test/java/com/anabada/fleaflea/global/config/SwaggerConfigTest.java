package com.anabada.fleaflea.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

class SwaggerConfigTest {

    private static final String SERVER_URL = "https://fleaflea.duckdns.org";

    @Test
    void openApiContainsServiceInformation() {
        OpenAPI openAPI = new SwaggerConfig().openAPI(SERVER_URL);

        assertThat(openAPI.getInfo().getTitle()).isEqualTo("FleaFlea API");
        assertThat(openAPI.getInfo().getDescription()).isEqualTo("FleaFlea API 문서");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("v1");
    }

    @Test
    void openApiUsesConfiguredHttpsServerUrl() {
        OpenAPI openAPI = new SwaggerConfig().openAPI(SERVER_URL);

        assertThat(openAPI.getServers())
                .singleElement()
                .extracting(server -> server.getUrl())
                .isEqualTo(SERVER_URL);
    }

    @Test
    void openApiContainsAccessTokenSecurityScheme() {
        OpenAPI openAPI = new SwaggerConfig().openAPI(SERVER_URL);

        SecurityScheme accessToken = openAPI.getComponents()
                .getSecuritySchemes()
                .get("accessToken");

        assertThat(accessToken.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(accessToken.getScheme()).isEqualTo("bearer");
        assertThat(accessToken.getBearerFormat()).isEqualTo("JWT");
        assertThat(openAPI.getSecurity().getFirst()).containsKey("accessToken");
    }
}
