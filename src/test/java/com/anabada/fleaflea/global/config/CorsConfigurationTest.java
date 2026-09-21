package com.anabada.fleaflea.global.config;

import com.anabada.fleaflea.domain.member.service.CustomMemberDetailsService;
import com.anabada.fleaflea.global.security.CustomAccessDeniedHandler;
import com.anabada.fleaflea.global.security.CustomAuthenticationEntryPoint;
import com.anabada.fleaflea.global.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CorsConfigurationTest {

    @Test
    void bindsCommaSeparatedOrigins() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "cors.allowed-origins",
                "http://localhost:5175,https://fleaflea.app"
        )));

        CorsProperties properties = binder
                .bind("cors", Bindable.of(CorsProperties.class))
                .orElseThrow(() -> new AssertionError("CORS properties were not bound"));

        assertThat(properties.allowedOrigins()).containsExactly(
                "http://localhost:5175",
                "https://fleaflea.app"
        );
    }

    @Test
    void usesConfiguredAllowedOrigins() {
        CorsProperties properties = new CorsProperties(List.of(
                "http://localhost:5175",
                "https://fleaflea.app"
        ));
        SecurityConfig securityConfig = new SecurityConfig(
                mock(CustomMemberDetailsService.class),
                mock(JwtAuthenticationFilter.class),
                mock(CustomAuthenticationEntryPoint.class),
                mock(CustomAccessDeniedHandler.class),
                properties
        );

        CorsConfigurationSource source =
                securityConfig.corsConfigurationSource();
        CorsConfiguration configuration = source.getCorsConfiguration(
                new MockHttpServletRequest("GET", "/api/v1/markets")
        );

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins())
                .containsExactly(
                        "http://localhost:5175",
                        "https://fleaflea.app"
                );
        assertThat(configuration.getAllowCredentials()).isTrue();
    }
}
