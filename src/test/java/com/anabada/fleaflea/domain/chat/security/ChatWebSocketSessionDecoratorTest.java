package com.anabada.fleaflea.domain.chat.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatWebSocketSessionDecoratorTest {

    @Test
    @DisplayName("토큰이 만료된 연결을 종료해 이후 채팅 수신도 중단한다")
    void closeExpiredSessions_closesExpiredConnection() throws Exception {
        ChatWebSocketSessionDecorator decorator = new ChatWebSocketSessionDecorator();
        WebSocketSession session = createSession(System.currentTimeMillis() - 1000);
        decorator.decorate(mock(WebSocketHandler.class)).afterConnectionEstablished(session);

        decorator.closeExpiredSessions();

        verify(session).close(CloseStatus.POLICY_VIOLATION.withReason("Access token expired"));
    }

    @Test
    @DisplayName("만료 전 연결은 유지한다")
    void closeExpiredSessions_keepsValidConnection() throws Exception {
        ChatWebSocketSessionDecorator decorator = new ChatWebSocketSessionDecorator();
        WebSocketSession session = createSession(System.currentTimeMillis() + 60000);
        decorator.decorate(mock(WebSocketHandler.class)).afterConnectionEstablished(session);

        decorator.closeExpiredSessions();

        verify(session, never()).close(any());
    }

    @Test
    @DisplayName("이미 종료된 연결은 만료 검사에서 제거한다")
    void afterConnectionClosed_removesSession() throws Exception {
        ChatWebSocketSessionDecorator decorator = new ChatWebSocketSessionDecorator();
        WebSocketSession session = createSession(System.currentTimeMillis() - 1000);
        WebSocketHandler handler = decorator.decorate(mock(WebSocketHandler.class));
        handler.afterConnectionEstablished(session);
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        decorator.closeExpiredSessions();

        verify(session, never()).close(any());
    }

    @Test
    @DisplayName("CONNECT 인증을 완료하지 않은 연결은 제한 시간이 지나면 종료한다")
    void closeExpiredSessions_closesUnauthenticatedConnection() throws Exception {
        ChatWebSocketSessionDecorator decorator = new ChatWebSocketSessionDecorator();
        WebSocketSession session = createSession(System.currentTimeMillis() + 60000);
        decorator.decorate(mock(WebSocketHandler.class)).afterConnectionEstablished(session);
        session.getAttributes().remove(ChatWebSocketAuthInterceptor.TOKEN_EXPIRATION_ATTRIBUTE);
        session.getAttributes().put("chatAuthenticationDeadline", System.currentTimeMillis() - 1000);

        decorator.closeExpiredSessions();

        verify(session).close(CloseStatus.POLICY_VIOLATION.withReason("Authentication timeout"));
    }

    private WebSocketSession createSession(long expiresAt) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session");
        when(session.getAttributes()).thenReturn(new HashMap<>(Map.of(
                ChatWebSocketAuthInterceptor.TOKEN_EXPIRATION_ATTRIBUTE, expiresAt
        )));
        return session;
    }
}
