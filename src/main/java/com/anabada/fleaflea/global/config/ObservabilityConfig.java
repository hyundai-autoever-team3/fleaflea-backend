package com.anabada.fleaflea.global.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    @Bean
    public MeterFilter sseHttpMeterFilter() {
        return MeterFilter.deny(id ->
                "http.server.requests".equals(id.getName())
                        && "/api/v1/notifications/subscribe".equals(id.getTag("uri"))
        );
    }

    @Bean
    public MeterBinder buildInfoMeterBinder(@Value("${app.version:local}") String version) {
        return registry -> Gauge.builder("fleaflea.build.info", () -> 1)
                .description("Application build information")
                .tag("version", version)
                .strongReference(true)
                .register(registry);
    }
}
