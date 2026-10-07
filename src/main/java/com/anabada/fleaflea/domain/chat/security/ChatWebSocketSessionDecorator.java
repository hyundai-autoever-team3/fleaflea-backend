package com.anabada.fleaflea.domain.chat.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChatWebSocketSessionDecorator implements WebSocketHandlerDecoratorFactory {

    private static final String AUTHENTICATION_DEADLINE_ATTRIBUTE = "chatAuthenticationDeadline";

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {

            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                session.getAttributes().put(AUTHENTICATION_DEADLINE_ATTRIBUTE, System.currentTimeMillis() + 10000);
                sessions.put(session.getId(), session);
                try {
                    super.afterConnectionEstablished(session);
                } catch (Exception exception) {
                    sessions.remove(session.getId());
                    throw exception;
                }
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                sessions.remove(session.getId());
                super.afterConnectionClosed(session, closeStatus);
            }
        };
    }

    @Scheduled(fixedDelay = 1000)
    public void closeExpiredSessions() {
        long now = System.currentTimeMillis();
        for (WebSocketSession session : sessions.values()) {
            Object expiration = session.getAttributes().get(ChatWebSocketAuthInterceptor.TOKEN_EXPIRATION_ATTRIBUTE);
            Object deadline = session.getAttributes().get(AUTHENTICATION_DEADLINE_ATTRIBUTE);
            boolean tokenExpired = expiration instanceof Long expiresAt && expiresAt <= now;
            boolean authenticationTimedOut = expiration == null && deadline instanceof Long authenticateBy
                    && authenticateBy <= now;
            if (tokenExpired || authenticationTimedOut) {
                try {
                    String reason = tokenExpired ? "Access token expired" : "Authentication timeout";
                    session.close(CloseStatus.POLICY_VIOLATION.withReason(reason));
                    sessions.remove(session.getId());
                } catch (IOException exception) {
                    log.warn("만료된 채팅 연결 종료 실패: sessionId={}", session.getId(), exception);
                }
            }
        }
    }

    public void closeRejectedSession(String sessionId) {
        WebSocketSession session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null) {
            return;
        }

        try {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Chat authentication or destination rejected"));
            sessions.remove(sessionId);
        } catch (IOException exception) {
            log.warn("거절된 채팅 연결 종료 실패: sessionId={}", sessionId, exception);
        }
    }
}
