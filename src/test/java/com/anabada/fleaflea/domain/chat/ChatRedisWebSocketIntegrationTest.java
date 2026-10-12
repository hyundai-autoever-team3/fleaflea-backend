package com.anabada.fleaflea.domain.chat;

import com.anabada.fleaflea.domain.chat.config.ChatRedisConfiguration;
import com.anabada.fleaflea.domain.chat.dto.ChatMessageResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatReadResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatTypingResponse;
import com.anabada.fleaflea.domain.chat.event.ChatEventPublisher;
import com.anabada.fleaflea.domain.chat.event.ChatMessageSentEvent;
import com.anabada.fleaflea.domain.chat.event.ChatMessagesReadEvent;
import com.anabada.fleaflea.domain.chat.event.ChatTypingChangedEvent;
import com.anabada.fleaflea.domain.chat.redis.RedisChatEventPublisher;
import com.anabada.fleaflea.domain.chat.redis.RedisChatEventSubscriber;
import com.anabada.fleaflea.domain.chat.service.ChatService;
import com.anabada.fleaflea.domain.member.service.CustomMemberDetailsService;
import com.anabada.fleaflea.fixture.ChatFixture;
import com.anabada.fleaflea.global.security.JwtTokenProvider;
import com.anabada.fleaflea.global.security.oauth2.CustomOidcUserService;
import com.anabada.fleaflea.global.security.oauth2.OAuth2FailureHandler;
import com.anabada.fleaflea.global.security.oauth2.OAuth2SuccessHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChatRedisWebSocketIntegrationTest {

    private final GenericContainer<?> redisContainer = new GenericContainer<>("redis:7.4")
            .withExposedPorts(6379);
    private final List<StompSession> stompSessions = new ArrayList<>();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private ConfigurableApplicationContext firstServer;
    private ConfigurableApplicationContext secondServer;
    private WebSocketStompClient stompClient;

    @BeforeAll
    void startTwoServersWithSharedRedis() {
        redisContainer.start();
        firstServer = startServer();
        secondServer = startServer();
    }

    @BeforeEach
    void startStompClient() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new ByteArrayMessageConverter() {

            @Override
            protected boolean supportsMimeType(MessageHeaders headers) {
                return true;
            }
        });
        firstServer.getBean(ChatWebSocketIntegrationTest.SubscriptionTracker.class).clear();
        secondServer.getBean(ChatWebSocketIntegrationTest.SubscriptionTracker.class).clear();
    }

    @AfterEach
    void disconnectStompSessions() {
        for (StompSession stompSession : stompSessions) {
            if (stompSession.isConnected()) {
                stompSession.disconnect();
            }
        }
        stompSessions.clear();
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    @AfterAll
    void stopServersAndRedis() {
        if (secondServer != null) {
            secondServer.close();
        }
        if (firstServer != null) {
            firstServer.close();
        }
        redisContainer.stop();
    }

    @Test
    @DisplayName("두 서버에 나뉜 같은 사용자의 세션과 상대 세션으로 메시지를 중복 없이 전달한다")
    void publishMessageSent_deliversToParticipantSessionsAcrossServers() throws Exception {
        BlockingQueue<String> senderOnFirstServer = subscribe(firstServer, 1L);
        BlockingQueue<String> senderOnSecondServer = subscribe(secondServer, 1L);
        BlockingQueue<String> receiverOnSecondServer = subscribe(secondServer, 2L);
        BlockingQueue<String> outsiderOnFirstServer = subscribe(firstServer, 3L);
        ChatMessageResponse chatMessageResponse = ChatMessageResponse.from(ChatFixture.createChatMessageWithId(
                20L, 10L, 1L, ChatFixture.createChatMessageSendRequest("다른 서버로 전달"),
                LocalDateTime.of(2026, 1, 1, 12, 0)
        ));

        firstServer.getBean(ChatEventPublisher.class).publishMessageSent(
                ChatMessageSentEvent.of(1L, 2L, chatMessageResponse)
        );

        for (BlockingQueue<String> participantMessages : List.of(
                senderOnFirstServer, senderOnSecondServer, receiverOnSecondServer
        )) {
            String receivedMessage = participantMessages.poll(5, TimeUnit.SECONDS);
            assertThat(receivedMessage).isNotNull();
            assertThat(jsonMapper.readTree(receivedMessage).get("type").asString()).isEqualTo("chat-message");
            assertThat(jsonMapper.treeToValue(jsonMapper.readTree(receivedMessage).get("payload"), ChatMessageResponse.class))
                    .isEqualTo(chatMessageResponse);
            assertThat(participantMessages.poll(200, TimeUnit.MILLISECONDS)).isNull();
        }
        assertThat(outsiderOnFirstServer.poll(200, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @DisplayName("다른 서버에서 발행한 읽음은 양쪽에 전달하고 입력 상태는 상대에게만 전달한다")
    void publishReadAndTyping_preservesRecipientRulesAcrossServers() throws Exception {
        BlockingQueue<String> senderMessages = subscribe(firstServer, 1L);
        BlockingQueue<String> receiverMessages = subscribe(secondServer, 2L);
        ChatReadResponse chatReadResponse = new ChatReadResponse(10L, 2L, 20L);

        secondServer.getBean(ChatEventPublisher.class).publishMessagesRead(
                ChatMessagesReadEvent.of(2L, 1L, chatReadResponse)
        );

        assertEventType(senderMessages, "chat-read");
        assertEventType(receiverMessages, "chat-read");

        ChatTypingResponse chatTypingResponse = ChatTypingResponse.from(
                ChatFixture.createChatRoomWithId(10L, 1L, 2L), 1L, true
        );
        firstServer.getBean(ChatEventPublisher.class).publishTypingChanged(
                ChatTypingChangedEvent.of(1L, 2L, chatTypingResponse)
        );

        assertEventType(receiverMessages, "chat-typing");
        assertThat(senderMessages.poll(300, TimeUnit.MILLISECONDS)).isNull();
        assertThat(receiverMessages.poll(200, TimeUnit.MILLISECONDS)).isNull();
    }

    private ConfigurableApplicationContext startServer() {
        return new SpringApplicationBuilder(
                ChatWebSocketIntegrationTest.TestApplication.class, TestServices.class,
                ChatRedisConfiguration.class, RedisChatEventPublisher.class, RedisChatEventSubscriber.class
        ).run(
                "--server.port=0",
                "--app.chat.redis.enabled=true",
                "--app.chat.redis.channel-prefix=test:chat:websocket",
                "--spring.data.redis.host=" + redisContainer.getHost(),
                "--spring.data.redis.port=" + redisContainer.getMappedPort(6379),
                "--spring.data.redis.password=",
                "--jwt.secret=chat-redis-test-secret-key-long-enough-for-hmac-signature-256",
                "--jwt.access-expiration=60000",
                "--jwt.refresh-expiration=120000",
                "--cors.allowed-origins[0]=http://localhost"
        );
    }

    private BlockingQueue<String> subscribe(
            ConfigurableApplicationContext server,
            Long memberId
    ) throws Exception {
        int serverPort = ((ServletWebServerApplicationContext) server).getWebServer().getPort();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.set("Authorization", "Bearer " + server.getBean(JwtTokenProvider.class).createAccessToken(memberId));
        StompSession stompSession = stompClient.connectAsync(
                "ws://localhost:" + serverPort + "/ws/chat",
                new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);
        stompSessions.add(stompSession);
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        stompSession.subscribe("/user/queue/chat", new StompFrameHandler() {

            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(
                    StompHeaders headers,
                    Object payload
            ) {
                messages.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });
        server.getBean(ChatWebSocketIntegrationTest.SubscriptionTracker.class)
                .awaitSubscription(memberId, "/user/queue/chat");

        return messages;
    }

    private void assertEventType(
            BlockingQueue<String> messages,
            String expectedType
    ) throws Exception {
        String receivedMessage = messages.poll(5, TimeUnit.SECONDS);
        assertThat(receivedMessage).isNotNull();
        assertThat(jsonMapper.readTree(receivedMessage).get("type").asString()).isEqualTo(expectedType);
    }

    @Configuration(proxyBeanMethods = false)
    static class TestServices {

        @Bean
        ChatService chatService() {
            return mock(ChatService.class);
        }

        @Bean
        CustomMemberDetailsService customMemberDetailsService() {
            return mock(CustomMemberDetailsService.class);
        }

        @Bean
        CustomOidcUserService customOidcUserService() {
            return mock(CustomOidcUserService.class);
        }

        @Bean
        OAuth2SuccessHandler oauth2SuccessHandler() {
            return mock(OAuth2SuccessHandler.class);
        }

        @Bean
        OAuth2FailureHandler oauth2FailureHandler() {
            return mock(OAuth2FailureHandler.class);
        }
    }
}
