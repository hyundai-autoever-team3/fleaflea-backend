package com.anabada.fleaflea.global.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRequestLogFilterTest {
    @Test
    @DisplayName("인증 전 요청 로그에도 설치 식별자를 노출하지 않는다")
    void requestLog_doesNotLogRequestBody() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpRequestLogFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            var request = new MockHttpServletRequest("POST", "/api/v1/notifications/devices");
            request.setContent("{\"fid\":\"c123456789012345678901\",\"platform\":\"WEB\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            new HttpRequestLogFilter().doFilter(request, new MockHttpServletResponse(),
                    (req, response) -> ((jakarta.servlet.http.HttpServletResponse) response).setStatus(401));
            assertThat(appender.list).hasSize(1);
            var event = appender.list.getFirst();
            assertThat(event.getFormattedMessage()).contains("/notifications/devices").doesNotContain("c123456789012345678901");
            assertThat(event.getKeyValuePairs().toString()).doesNotContain("c123456789012345678901");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
