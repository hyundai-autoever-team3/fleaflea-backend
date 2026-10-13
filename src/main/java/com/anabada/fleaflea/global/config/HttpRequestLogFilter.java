package com.anabada.fleaflea.global.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpRequestLogFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().equals("/readyz")
                || request.getRequestURI().startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            String route = pattern instanceof String value ? value : "UNMATCHED";
            String uri = request.getRequestURI();
            Object code = request.getAttribute("fleaflea.code");
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            LoggingEventBuilder event = log.atInfo()
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("route", route)
                    .addKeyValue("uri", uri)
                    .addKeyValue("status", response.getStatus())
                    .addKeyValue("durationMs", durationMs);

            if (code != null) {
                event = event.addKeyValue("code", code);
            }

            String errorSuffix = code == null
                    ? ""
                    : " code=" + code;
            event.log("HTTP {} {} -> {} ({} ms){}",
                    request.getMethod(), uri, response.getStatus(), durationMs, errorSuffix);
        }
    }
}
