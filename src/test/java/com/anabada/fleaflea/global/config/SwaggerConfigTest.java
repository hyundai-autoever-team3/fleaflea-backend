package com.anabada.fleaflea.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

class SwaggerConfigTest {

    @Test
    void openApiContainsServiceInformation() {
        OpenAPI openAPI = new SwaggerConfig().openAPI();

        assertThat(openAPI.getInfo().getTitle()).isEqualTo("FleaFlea API");
        assertThat(openAPI.getInfo().getDescription()).isEqualTo("FleaFlea API 문서");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("v1");
    }

    @Test
    void openApiContainsAccessTokenSecurityScheme() {
        OpenAPI openAPI = new SwaggerConfig().openAPI();

        SecurityScheme accessToken = openAPI.getComponents()
                .getSecuritySchemes()
                .get("accessToken");

        assertThat(accessToken.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(accessToken.getScheme()).isEqualTo("bearer");
        assertThat(accessToken.getBearerFormat()).isEqualTo("JWT");
        assertThat(openAPI.getSecurity().getFirst()).containsKey("accessToken");
    }
}
