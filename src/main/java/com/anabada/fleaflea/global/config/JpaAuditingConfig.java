package com.anabada.fleaflea.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        // PostgreSQL TIMESTAMP(6)와 저장 직후 응답의 정밀도를 일치시킨다.
        return () -> Optional.of(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
    }
}
