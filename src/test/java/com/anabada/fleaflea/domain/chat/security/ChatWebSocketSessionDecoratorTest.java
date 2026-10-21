package com.anabada.fleaflea.domain.chat.security;

import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.fixture.MemberFixture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatWebSocketSessionDecoratorTest {

    @Test
    @DisplayName("토큰이 만료된 연결을 종료해 이후 채팅 수신도 중단한다")
    void closeExpiredSessions_closesExpiredConnection() throws Exception {
        ChatWebSocketSessionDecorator decorator = new ChatWebSocketSessionDecorator(mock(MemberRepository.class));
        WebSocketSession session = createSession(System.currentTimeMillis() - 1000);
        decorator.decorate(mock(WebSocketHandler.class)).afterConnectionEstablished(session);

        decorator.closeExpiredSessions();

        verify(session).close(CloseStatus.POLICY_VIOLATION.withReason("Access token expired"));
    }

    @Test
    @DisplayName("만료 전 연결은 유지한다")
    void closeExpiredSessions_keepsValidConnection() throws Exception {
        ChatWebSocketSessionDecorator decorator = new ChatWebSocketSessionDecorator(mock(MemberRepository.class));
        WebSocketSession session = createSession(System.currentTimeMillis() + 60000);
        decorator.decorate(mock(WebSocketHandler.class)).afterConnectionEstablished(session);

        decorator.closeExpiredSessions();

        verify(session, never()).close(any());
    }

    @Test
    @DisplayName("이미 종료된 연결은 만료 검사에서 제거한다")
    void afterConnectionClosed_removesSession() throws Exception {
        ChatWebSocketSessionDecorator decorator = new ChatWebSocketSessionDecorator(mock(MemberRepository.class));
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
        ChatWebSocketSessionDecorator decorator = new ChatWebSocketSessionDecorator(mock(MemberRepository.class));
        WebSocketSession session = createSession(System.currentTimeMillis() + 60000);
        decorator.decorate(mock(WebSocketHandler.class)).afterConnectionEstablished(session);
        session.getAttributes().remove(ChatWebSocketAuthInterceptor.TOKEN_EXPIRATION_ATTRIBUTE);
        session.getAttributes().put("chatAuthenticationDeadline", System.currentTimeMillis() - 1000);

        decorator.closeExpiredSessions();

        verify(session).close(CloseStatus.POLICY_VIOLATION.withReason("Authentication timeout"));
    }

    @Test
    @DisplayName("회원 조회 도중 다른 회원이 접속해도 신규 연결은 종료하지 않는다")
    void closeDeletedMemberSessions_keepsSessionConnectedDuringLookup() throws Exception {
        MemberRepository repository = mock(MemberRepository.class);
        ChatWebSocketSessionDecorator decorator = new ChatWebSocketSessionDecorator(repository);
        WebSocketHandler handler = decorator.decorate(mock(WebSocketHandler.class));
        WebSocketSession existing = createSession(System.currentTimeMillis() + 60000);
        existing.getAttributes().put(ChatWebSocketAuthInterceptor.MEMBER_ID_ATTRIBUTE, 1L);
        handler.afterConnectionEstablished(existing);
        WebSocketSession incoming = createSession(System.currentTimeMillis() + 60000);
        when(incoming.getId()).thenReturn("incoming");
        incoming.getAttributes().put(ChatWebSocketAuthInterceptor.MEMBER_ID_ATTRIBUTE, 2L);
        when(repository.findAllById(Set.of(1L))).thenAnswer(invocation -> {
            handler.afterConnectionEstablished(incoming);
            return List.of(MemberFixture.createMember(1L));
        });

        decorator.closeDeletedMemberSessions();

        verify(repository).findAllById(Set.of(1L));
        verify(existing, never()).close(any());
        verify(incoming, never()).close(any());
    }

    @Test
    @DisplayName("회원 조회 도중 기존 세션이 다른 회원으로 인증되어도 연결을 유지한다")
    void closeDeletedMemberSessions_keepsSessionAuthenticatedDuringLookup() throws Exception {
        MemberRepository repository = mock(MemberRepository.class);
        ChatWebSocketSessionDecorator decorator = new ChatWebSocketSessionDecorator(repository);
        WebSocketHandler handler = decorator.decorate(mock(WebSocketHandler.class));
        WebSocketSession existing = createSession(System.currentTimeMillis() + 60000);
        existing.getAttributes().put(ChatWebSocketAuthInterceptor.MEMBER_ID_ATTRIBUTE, 1L);
        handler.afterConnectionEstablished(existing);
        WebSocketSession incoming = createSession(System.currentTimeMillis() + 60000);
        when(incoming.getId()).thenReturn("incoming");
        handler.afterConnectionEstablished(incoming);
        when(repository.findAllById(Set.of(1L))).thenAnswer(invocation -> {
            incoming.getAttributes().put(ChatWebSocketAuthInterceptor.MEMBER_ID_ATTRIBUTE, 2L);
            return List.of(MemberFixture.createMember(1L));
        });

        decorator.closeDeletedMemberSessions();

        verify(incoming, never()).close(any());
    }

    @Test
    @DisplayName("존재하지 않는 회원의 세션은 종료하고 정상 회원의 세션은 유지한다")
    void closeDeletedMemberSessions_closesOnlyDeletedMember() throws Exception {
        MemberRepository repository = mock(MemberRepository.class);
        ChatWebSocketSessionDecorator decorator = new ChatWebSocketSessionDecorator(repository);
        WebSocketHandler handler = decorator.decorate(mock(WebSocketHandler.class));
        WebSocketSession deleted = createSession(System.currentTimeMillis() + 60000);
        deleted.getAttributes().put(ChatWebSocketAuthInterceptor.MEMBER_ID_ATTRIBUTE, 1L);
        handler.afterConnectionEstablished(deleted);
        WebSocketSession active = createSession(System.currentTimeMillis() + 60000);
        when(active.getId()).thenReturn("active");
        active.getAttributes().put(ChatWebSocketAuthInterceptor.MEMBER_ID_ATTRIBUTE, 2L);
        handler.afterConnectionEstablished(active);
        when(repository.findAllById(Set.of(1L, 2L)))
                .thenReturn(List.of(MemberFixture.createMember(2L)));

        decorator.closeDeletedMemberSessions();

        verify(deleted).close(any(CloseStatus.class));
        verify(active, never()).close(any());
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
