package com.anabada.fleaflea.domain.chat.security;

import com.anabada.fleaflea.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatWebSocketAuthInterceptorTest {

    private JwtTokenProvider jwtTokenProvider;
    private ChatWebSocketAuthInterceptor chatWebSocketAuthInterceptor;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider("chat-websocket-test-secret-key-at-least-32-bytes", 60000, 60000);
        chatWebSocketAuthInterceptor = new ChatWebSocketAuthInterceptor(
                jwtTokenProvider, new ChatWebSocketSessionDecorator()
        );
    }

    @Test
    @DisplayName("CONNECT 액세스 토큰으로 회원 이름과 세션 만료 시각을 설정한다")
    void connect_authenticatesMember() {
        StompHeaderAccessor accessor = createAccessor(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(1L));

        chatWebSocketAuthInterceptor.preSend(createMessage(accessor), null);

        assertThat(accessor.getUser().getName()).isEqualTo("1");
        assertThat(accessor.getSessionAttributes())
                .containsKey(ChatWebSocketAuthInterceptor.TOKEN_EXPIRATION_ATTRIBUTE);
        assertThat(accessor.getFirstNativeHeader("Authorization")).isNull();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {"", "Bearer invalid", "invalid"})
    @DisplayName("토큰이 없거나 형식이 잘못된 연결은 거절한다")
    void connect_rejectsInvalidAuthorization(String authorization) {
        StompHeaderAccessor accessor = createAccessor(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", authorization);

        assertThatThrownBy(() -> chatWebSocketAuthInterceptor.preSend(createMessage(accessor), null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("리프레시 토큰으로 채팅에 연결할 수 없다")
    void connect_rejectsRefreshToken() {
        StompHeaderAccessor accessor = createAccessor(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + jwtTokenProvider.createRefreshToken(1L));

        assertThatThrownBy(() -> chatWebSocketAuthInterceptor.preSend(createMessage(accessor), null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {"/queue/chat", "/topic/chat", "/user/2/queue/chat", "/app/chat/rooms/1/messages"})
    @DisplayName("다른 회원이나 공용 경로를 구독할 수 없다")
    void subscribe_rejectsOtherDestinations(String destination) {
        StompHeaderAccessor accessor = createAuthenticatedAccessor(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);

        assertThatThrownBy(() -> chatWebSocketAuthInterceptor.preSend(createMessage(accessor), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {"/user/queue/chat", "/user/queue/chat-acks", "/user/queue/chat-errors"})
    @DisplayName("본인의 채팅 이벤트와 전송 결과 및 오류만 구독할 수 있다")
    void subscribe_acceptsPersonalDestinations(String destination) {
        StompHeaderAccessor accessor = createAuthenticatedAccessor(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        Message<byte[]> message = createMessage(accessor);

        assertThat(chatWebSocketAuthInterceptor.preSend(message, null)).isSameAs(message);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {"/queue/chat", "/user/queue/chat", "/topic/chat", "/app/notifications", "/app/chat/rooms/1/messages/extra"})
    @DisplayName("브로커 직접 전송과 채팅 외 경로 전송을 차단한다")
    void send_rejectsOtherDestinations(String destination) {
        StompHeaderAccessor accessor = createAuthenticatedAccessor(StompCommand.SEND);
        accessor.setDestination(destination);

        assertThatThrownBy(() -> chatWebSocketAuthInterceptor.preSend(createMessage(accessor), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {"/app/chat/rooms/1/messages", "/app/chat/rooms/1/read"})
    @DisplayName("채팅 메시지와 읽음 처리 경로로 전송할 수 있다")
    void send_acceptsChatDestinations(String destination) {
        StompHeaderAccessor accessor = createAuthenticatedAccessor(StompCommand.SEND);
        accessor.setDestination(destination);
        Message<byte[]> message = createMessage(accessor);

        assertThat(chatWebSocketAuthInterceptor.preSend(message, null)).isSameAs(message);
    }

    @Test
    @DisplayName("Principal만 위조하고 CONNECT 인증을 생략한 요청은 거절한다")
    void send_requiresAuthenticatedSession() {
        StompHeaderAccessor accessor = createAccessor(StompCommand.SEND);
        accessor.setUser(new UsernamePasswordAuthenticationToken("1", null, List.of()));
        accessor.setDestination("/app/chat/rooms/1/messages");

        assertThatThrownBy(() -> chatWebSocketAuthInterceptor.preSend(createMessage(accessor), null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("연결 이후에도 만료된 액세스 토큰으로 전송할 수 없다")
    void send_rechecksTokenExpiration() {
        StompHeaderAccessor accessor = createAuthenticatedAccessor(StompCommand.SEND);
        accessor.setDestination("/app/chat/rooms/1/messages");
        accessor.getSessionAttributes().put(ChatWebSocketAuthInterceptor.TOKEN_EXPIRATION_ATTRIBUTE,
                System.currentTimeMillis() - 1000);

        assertThatThrownBy(() -> chatWebSocketAuthInterceptor.preSend(createMessage(accessor), null))
                .isInstanceOf(BadCredentialsException.class);
    }

    private StompHeaderAccessor createAuthenticatedAccessor(StompCommand command) {
        StompHeaderAccessor connect = createAccessor(StompCommand.CONNECT);
        connect.setNativeHeader("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(1L));
        chatWebSocketAuthInterceptor.preSend(createMessage(connect), null);

        StompHeaderAccessor accessor = createAccessor(command);
        accessor.setUser(connect.getUser());
        accessor.setSessionAttributes(connect.getSessionAttributes());
        return accessor;
    }

    private StompHeaderAccessor createAccessor(StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId("chat-session");
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private Message<byte[]> createMessage(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
