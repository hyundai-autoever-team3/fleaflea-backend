package com.anabada.fleaflea.global.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
            Object errorCode = request.getAttribute("fleaflea.errorCode");
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            LoggingEventBuilder event = log.atInfo()
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("route", route)
                    .addKeyValue("status", response.getStatus())
                    .addKeyValue("ㅁ", durationMs);

            if (errorCode != null) {
                event = event.addKeyValue("errorCode", errorCode);
            }

            String traceId = MDC.get("trace_id");
            String traceSuffix = traceId == null || traceId.isBlank()
                    ? ""
                    : " traceId=" + traceId;
            String errorSuffix = errorCode == null
                    ? ""
                    : " errorCode=" + errorCode;
            event.log("HTTP {} {} -> {} ({} ms){}{}",
                    request.getMethod(), route, response.getStatus(), durationMs, traceSuffix, errorSuffix);
        }
    }
}
