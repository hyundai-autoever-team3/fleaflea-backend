package com.anabada.fleaflea.domain.chat;

import com.anabada.fleaflea.domain.chat.config.ChatWebSocketConfig;
import com.anabada.fleaflea.domain.chat.controller.ChatSocketController;
import com.anabada.fleaflea.domain.chat.dto.ChatMessageResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatMessageSendRequest;
import com.anabada.fleaflea.domain.chat.dto.ChatReadResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatTypingResponse;
import com.anabada.fleaflea.domain.chat.event.ChatEvent;
import com.anabada.fleaflea.domain.chat.event.ChatEventListener;
import com.anabada.fleaflea.domain.chat.exception.ChatFriendRequiredException;
import com.anabada.fleaflea.domain.chat.security.ChatWebSocketAuthInterceptor;
import com.anabada.fleaflea.domain.chat.security.ChatWebSocketSessionDecorator;
import com.anabada.fleaflea.domain.chat.service.ChatService;
import com.anabada.fleaflea.domain.member.service.CustomMemberDetailsService;
import com.anabada.fleaflea.fixture.ChatFixture;
import com.anabada.fleaflea.global.config.SchedulingConfig;
import com.anabada.fleaflea.global.config.SecurityConfig;
import com.anabada.fleaflea.global.security.CustomAccessDeniedHandler;
import com.anabada.fleaflea.global.security.CustomAuthenticationEntryPoint;
import com.anabada.fleaflea.global.security.JwtAuthenticationFilter;
import com.anabada.fleaflea.global.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = ChatWebSocketIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "cors.allowed-origins[0]=http://localhost",
                "jwt.secret=chat-websocket-test-secret-key-at-least-32-bytes",
                "jwt.access-expiration=60000",
                "jwt.refresh-expiration=60000"
        }
)
class ChatWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ChatEventListener chatEventListener;

    @Autowired
    private SubscriptionTracker subscriptionTracker;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private CustomMemberDetailsService customMemberDetailsService;

    private WebSocketStompClient stompClient;
    private final List<StompSession> sessions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new ByteArrayMessageConverter() {

            @Override
            protected boolean supportsMimeType(MessageHeaders headers) {
                return true;
            }
        });
        subscriptionTracker.clear();
    }

    @AfterEach
    void tearDown() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
        stompClient.stop();
    }

    @Test
    @DisplayName("실제 소켓 전송은 인증 회원을 사용하고 저장 결과를 요청 세션에 반환한다")
    void sendMessage_returnsSavedMessageToSender() throws Exception {
        ChatMessageSendRequest request = ChatFixture.createSendRequest("a".repeat(2000));
        ChatMessageResponse response = createMessageResponse(request);
        when(chatService.sendMessage(eq(1L), eq(10L), any(ChatMessageSendRequest.class))).thenReturn(response);
        StompSession session = connect(1L);
        BlockingQueue<String> acknowledgements = subscribe(session, 1L, "/user/queue/chat-acks");

        sendJson(session, "/app/chat/rooms/10/messages", JsonMapper.builder().build().writeValueAsString(request));

        String acknowledgement = acknowledgements.poll(5, TimeUnit.SECONDS);
        assertThat(acknowledgement).isNotNull();
        ChatMessageResponse actual = JsonMapper.builder().build().readValue(acknowledgement, ChatMessageResponse.class);
        assertThat(actual).isEqualTo(response);
        verify(chatService).sendMessage(1L, 10L, request);
    }

    @Test
    @DisplayName("읽음 처리도 소켓으로 전달하고 서비스의 결과를 반환한다")
    void markMessagesAsRead_returnsReadReceipt() throws Exception {
        ChatReadResponse response = new ChatReadResponse(10L, 1L, 20L);
        when(chatService.markMessagesAsRead(1L, 10L, 20L)).thenReturn(response);
        StompSession session = connect(1L);
        BlockingQueue<String> acknowledgements = subscribe(session, 1L, "/user/queue/chat-acks");

        sendJson(session, "/app/chat/rooms/10/read", "{\"messageId\":20}");

        String acknowledgement = acknowledgements.poll(5, TimeUnit.SECONDS);
        assertThat(acknowledgement).isNotNull();
        assertThat(JsonMapper.builder().build().readValue(acknowledgement, ChatReadResponse.class))
                .isEqualTo(response);
        verify(chatService).markMessagesAsRead(1L, 10L, 20L);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    @DisplayName("잘못된 JSON과 필수값은 소켓 오류로 반환하고 서비스를 호출하지 않는다")
    void sendMessage_rejectsInvalidRequest(String caseName, String destination, String body) throws Exception {
        StompSession session = connect(1L);
        BlockingQueue<String> errors = subscribe(session, 1L, "/user/queue/chat-errors");

        sendJson(session, destination, body);

        String error = errors.poll(5, TimeUnit.SECONDS);
        assertThat(error).isNotNull().contains("INVALID_REQUEST");
        verifyNoInteractions(chatService);
    }

    @Test
    @DisplayName("친구 관계 검증 실패는 기존 채팅 전용 오류 코드로 반환한다")
    void sendMessage_returnsDomainError() throws Exception {
        when(chatService.sendMessage(eq(1L), eq(10L), any(ChatMessageSendRequest.class)))
                .thenThrow(new ChatFriendRequiredException());
        StompSession session = connect(1L);
        BlockingQueue<String> errors = subscribe(session, 1L, "/user/queue/chat-errors");

        sendJson(session, "/app/chat/rooms/10/messages", JsonMapper.builder().build()
                .writeValueAsString(ChatFixture.createSendRequest("친구만")));

        assertThat(errors.poll(5, TimeUnit.SECONDS)).isNotNull().contains("CHAT_FRIEND_REQUIRED");
    }

    @Test
    @DisplayName("채팅 이벤트는 두 참여자에게 전달하고 제삼자에게는 전달하지 않는다")
    void chatEvent_isDeliveredOnlyToParticipants() throws Exception {
        BlockingQueue<String> senderEvents = subscribe(connect(1L), 1L, "/user/queue/chat");
        BlockingQueue<String> receiverEvents = subscribe(connect(2L), 2L, "/user/queue/chat");
        BlockingQueue<String> outsiderEvents = subscribe(connect(3L), 3L, "/user/queue/chat");
        ChatMessageResponse response = createMessageResponse(ChatFixture.createSendRequest("안녕하세요"));

        chatEventListener.onChatEvent(new ChatEvent(1L, 2L, ChatEvent.MESSAGE_SENT, response));

        assertThat(senderEvents.poll(5, TimeUnit.SECONDS)).isNotNull().contains("chat-message", "안녕하세요");
        assertThat(receiverEvents.poll(5, TimeUnit.SECONDS)).isNotNull().contains("chat-message", "안녕하세요");
        assertThat(outsiderEvents.poll(300, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @DisplayName("소켓 입력 상태는 인증 회원으로 처리하고 상대방에게만 전달한다")
    void updateTypingStatus_deliversOnlyToOtherMember() throws Exception {
        StompSession sender = connect(1L);
        BlockingQueue<String> senderEvents = subscribe(sender, 1L, "/user/queue/chat");
        BlockingQueue<String> receiverEvents = subscribe(connect(2L), 2L, "/user/queue/chat");
        BlockingQueue<String> outsiderEvents = subscribe(connect(3L), 3L, "/user/queue/chat");
        doAnswer(invocation -> {
            chatEventListener.onChatEvent(new ChatEvent(
                    1L, 2L, ChatEvent.TYPING_CHANGED,
                    ChatTypingResponse.from(ChatFixture.createChatRoomWithId(10L, 1L, 2L), 1L,
                            invocation.getArgument(2))
            ));
            return null;
        }).when(chatService).updateTypingStatus(eq(1L), eq(10L), any(Boolean.class));

        sendJson(sender, "/app/chat/rooms/10/typing", "{\"typing\":true}");

        String event = receiverEvents.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull().contains("chat-typing");
        assertThat(JsonMapper.builder().build().readTree(event).get("payload").get("typing").asBoolean()).isTrue();
        verify(chatService).updateTypingStatus(1L, 10L, true);
        assertThat(senderEvents.poll(300, TimeUnit.MILLISECONDS)).isNull();
        assertThat(outsiderEvents.poll(300, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @DisplayName("허용되지 않은 웹 Origin의 WebSocket 업그레이드 요청을 거절한다")
    void handshake_rejectsUntrustedOrigin() {
        assertThatThrownBy(() -> HttpClient.newHttpClient().newWebSocketBuilder()
                .header("Origin", "https://untrusted.example")
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/chat"), new WebSocket.Listener() {})
                .get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(WebSocketHandshakeException.class);
    }

    @Test
    @DisplayName("인증 헤더가 없는 STOMP 연결을 정책 위반으로 종료한다")
    void connect_requiresAccessToken() throws Exception {
        BlockingQueue<Integer> closeStatuses = new LinkedBlockingQueue<>();
        try (HttpClient httpClient = HttpClient.newHttpClient()) {
            WebSocket socket = httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create("ws://localhost:" + port + "/ws/chat"), new WebSocket.Listener() {

                        @Override
                        public void onOpen(WebSocket webSocket) {
                            webSocket.request(1);
                        }

                        @Override
                        public CompletionStage<?> onText(
                                WebSocket webSocket, CharSequence data, boolean last
                        ) {
                            webSocket.request(1);
                            return null;
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                            closeStatuses.add(statusCode);
                            return null;
                        }
                    }).get(5, TimeUnit.SECONDS);
            try {
                socket.sendText("CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n\u0000", true)
                        .get(5, TimeUnit.SECONDS);

                assertThat(closeStatuses.poll(5, TimeUnit.SECONDS)).isEqualTo(1008);
            } finally {
                socket.abort();
            }
        }

        verifyNoInteractions(chatService);
    }

    private void sendJson(StompSession session, String destination, String body) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination(destination);
        headers.setContentType(MediaType.APPLICATION_JSON);
        session.send(headers, body.getBytes(StandardCharsets.UTF_8));
    }

    private StompSession connect(Long memberId) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.set("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(memberId));
        StompSession session = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws/chat", new WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private BlockingQueue<String> subscribe(StompSession session, Long memberId, String destination) throws Exception {
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {

            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });
        subscriptionTracker.awaitSubscription(memberId, destination);
        return messages;
    }

    private ChatMessageResponse createMessageResponse(ChatMessageSendRequest request) {
        return ChatMessageResponse.from(ChatFixture.createChatMessageWithId(
                20L, 10L, 1L, request, LocalDateTime.of(2026, 1, 1, 12, 0)
        ));
    }

    private static Stream<Arguments> invalidRequests() {
        String messages = "/app/chat/rooms/10/messages";
        String uuid = "\"550e8400-e29b-41d4-a716-446655440000\"";
        return Stream.of(
                Arguments.of("빈 객체", messages, "{}"),
                Arguments.of("본문 null", messages, "null"),
                Arguments.of("잘못된 JSON", messages, "{"),
                Arguments.of("공백 메시지", messages, "{\"content\":\"   \",\"clientMessageId\":" + uuid + "}"),
                Arguments.of("길이 초과", messages, "{\"content\":\"" + "a".repeat(2001) + "\",\"clientMessageId\":" + uuid + "}"),
                Arguments.of("UUID 누락", messages, "{\"content\":\"안녕\"}"),
                Arguments.of("UUID 형식 오류", messages, "{\"content\":\"안녕\",\"clientMessageId\":\"invalid\"}"),
                Arguments.of("입력 상태 누락", "/app/chat/rooms/10/typing", "{}"),
                Arguments.of("입력 상태 null", "/app/chat/rooms/10/typing", "{\"typing\":null}"),
                Arguments.of("읽음 ID null", "/app/chat/rooms/10/read", "{\"messageId\":null}"),
                Arguments.of("읽음 ID 누락", "/app/chat/rooms/10/read", "{}"),
                Arguments.of("방 ID 범위 초과", "/app/chat/rooms/9223372036854775808/messages",
                        "{\"content\":\"안녕\",\"clientMessageId\":" + uuid + "}")
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
    @Import({ChatWebSocketConfig.class, ChatWebSocketAuthInterceptor.class, ChatWebSocketSessionDecorator.class,
            ChatSocketController.class, ChatEventListener.class, JwtTokenProvider.class, SchedulingConfig.class,
            SecurityConfig.class, JwtAuthenticationFilter.class, CustomAuthenticationEntryPoint.class,
            CustomAccessDeniedHandler.class})
    static class TestApplication {

        @Bean
        SubscriptionTracker subscriptionTracker() {
            return new SubscriptionTracker();
        }
    }

    static class SubscriptionTracker {

        private final BlockingQueue<SessionSubscribeEvent> subscriptions = new LinkedBlockingQueue<>();

        @EventListener
        public void onSubscribe(SessionSubscribeEvent event) {
            subscriptions.add(event);
        }

        void clear() {
            subscriptions.clear();
        }

        void awaitSubscription(Long memberId, String destination) throws Exception {
            SessionSubscribeEvent event = subscriptions.poll(5, TimeUnit.SECONDS);
            assertThat(event).isNotNull();
            assertThat(event.getUser().getName()).isEqualTo(memberId.toString());
            assertThat(StompHeaderAccessor.wrap(event.getMessage()).getDestination()).isEqualTo(destination);
        }
    }
}
