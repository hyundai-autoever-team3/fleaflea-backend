package com.anabada.fleaflea.global.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(
        @NotEmpty List<@NotBlank String> allowedOrigins
) {
    public CorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : List.copyOf(allowedOrigins);
    }
}
