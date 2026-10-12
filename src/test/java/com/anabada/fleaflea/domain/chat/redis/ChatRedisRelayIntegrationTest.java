package com.anabada.fleaflea.domain.chat.redis;

import com.anabada.fleaflea.domain.chat.config.ChatRedisConfiguration;
import com.anabada.fleaflea.domain.chat.config.ChatRedisProperties;
import com.anabada.fleaflea.domain.chat.dto.ChatMessageResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatReadResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatSocketEventResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatTypingResponse;
import com.anabada.fleaflea.domain.chat.event.ChatEventType;
import com.anabada.fleaflea.domain.chat.event.ChatMessageSentEvent;
import com.anabada.fleaflea.domain.chat.event.ChatMessagesReadEvent;
import com.anabada.fleaflea.domain.chat.event.ChatSocketEventSender;
import com.anabada.fleaflea.domain.chat.event.ChatTypingChangedEvent;
import com.anabada.fleaflea.fixture.ChatFixture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChatRedisRelayIntegrationTest {

    private final GenericContainer<?> redisContainer = new GenericContainer<>("redis:7.4")
            .withExposedPorts(6379);
    private final ChatRedisProperties chatRedisProperties = new ChatRedisProperties("test:chat:relay");
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final SimpMessagingTemplate firstServerMessagingTemplate = mock(SimpMessagingTemplate.class);
    private final SimpMessagingTemplate secondServerMessagingTemplate = mock(SimpMessagingTemplate.class);

    private LettuceConnectionFactory redisConnectionFactory;
    private StringRedisTemplate stringRedisTemplate;
    private RedisChatEventPublisher redisChatEventPublisher;
    private RedisMessageListenerContainer firstServerListenerContainer;
    private RedisMessageListenerContainer secondServerListenerContainer;

    @BeforeAll
    void startRedisAndServerSubscribers() {
        redisContainer.start();
        RedisStandaloneConfiguration redisConfiguration = new RedisStandaloneConfiguration(
                redisContainer.getHost(), redisContainer.getMappedPort(6379)
        );
        redisConnectionFactory = new LettuceConnectionFactory(redisConfiguration);
        redisConnectionFactory.afterPropertiesSet();
        redisConnectionFactory.start();
        stringRedisTemplate = new StringRedisTemplate(redisConnectionFactory);
        redisChatEventPublisher = new RedisChatEventPublisher(stringRedisTemplate, objectMapper, chatRedisProperties);
        firstServerListenerContainer = startServerSubscriber(firstServerMessagingTemplate);
        secondServerListenerContainer = startServerSubscriber(secondServerMessagingTemplate);
    }

    @BeforeEach
    void clearServerDeliveries() {
        clearInvocations(firstServerMessagingTemplate, secondServerMessagingTemplate);
    }

    @AfterAll
    void stopRedisAndServerSubscribers() throws Exception {
        if (firstServerListenerContainer != null) {
            firstServerListenerContainer.destroy();
        }
        if (secondServerListenerContainer != null) {
            secondServerListenerContainer.destroy();
        }
        if (redisConnectionFactory != null) {
            redisConnectionFactory.destroy();
        }
        redisContainer.stop();
    }

    @Test
    @DisplayName("서로 다른 서버의 구독자가 메시지 이벤트를 참여자별 한 번씩 전달한다")
    void publishMessageSent_deliversOnceOnEachServer() {
        ChatMessageResponse chatMessageResponse = ChatMessageResponse.from(ChatFixture.createChatMessageWithId(
                20L, 10L, 1L, ChatFixture.createChatMessageSendRequest("서버 간 메시지"),
                LocalDateTime.of(2026, 1, 1, 12, 0)
        ));
        ChatMessageSentEvent chatMessageSentEvent = ChatMessageSentEvent.of(1L, 2L, chatMessageResponse);

        redisChatEventPublisher.publishMessageSent(chatMessageSentEvent);

        assertParticipantDeliveries(firstServerMessagingTemplate, ChatSocketEventResponse.from(chatMessageSentEvent));
        assertParticipantDeliveries(secondServerMessagingTemplate, ChatSocketEventResponse.from(chatMessageSentEvent));
    }

    @Test
    @DisplayName("읽음 이벤트도 다른 서버로 전달되며 JSON 변환 후 타입과 읽음 위치를 유지한다")
    void publishMessagesRead_preservesReadReceiptOnEachServer() {
        ChatMessagesReadEvent chatMessagesReadEvent = ChatMessagesReadEvent.of(
                1L, 2L, new ChatReadResponse(10L, 1L, 20L)
        );

        redisChatEventPublisher.publishMessagesRead(chatMessagesReadEvent);

        assertParticipantDeliveries(firstServerMessagingTemplate, ChatSocketEventResponse.from(chatMessagesReadEvent));
        assertParticipantDeliveries(secondServerMessagingTemplate, ChatSocketEventResponse.from(chatMessagesReadEvent));
    }

    @Test
    @DisplayName("입력 상태는 각 서버에서 상대에게만 전달한다")
    void publishTypingChanged_deliversOnlyToFriendOnEachServer() {
        ChatTypingResponse chatTypingResponse = ChatTypingResponse.from(
                ChatFixture.createChatRoomWithId(10L, 1L, 2L), 1L, true
        );
        ChatTypingChangedEvent chatTypingChangedEvent = ChatTypingChangedEvent.of(1L, 2L, chatTypingResponse);

        redisChatEventPublisher.publishTypingChanged(chatTypingChangedEvent);

        ChatSocketEventResponse<ChatTypingResponse> chatSocketEventResponse = ChatSocketEventResponse.from(chatTypingChangedEvent);
        verify(firstServerMessagingTemplate, timeout(3000)).convertAndSendToUser("2", "/queue/chat", chatSocketEventResponse);
        verify(secondServerMessagingTemplate, timeout(3000)).convertAndSendToUser("2", "/queue/chat", chatSocketEventResponse);
        verify(firstServerMessagingTemplate, after(200).never()).convertAndSendToUser(eq("1"), anyString(), any());
        verify(secondServerMessagingTemplate, after(200).never()).convertAndSendToUser(eq("1"), anyString(), any());
    }

    @Test
    @DisplayName("다른 환경의 채널과 잘못된 JSON은 전달하지 않으며 다음 정상 이벤트를 처리한다")
    void subscriber_invalidEventAndForeignChannel_doesNotInterruptNextEvent() {
        assertThat(stringRedisTemplate.convertAndSend("other:chat:chat-message", "{}")).isZero();
        stringRedisTemplate.convertAndSend(chatRedisProperties.channelFor(ChatEventType.MESSAGE_SENT), "invalid-json");
        stringRedisTemplate.convertAndSend(chatRedisProperties.channelFor(ChatEventType.MESSAGE_SENT), "{}");
        stringRedisTemplate.convertAndSend(chatRedisProperties.channelFor(ChatEventType.MESSAGE_SENT), "null");

        ChatMessagesReadEvent chatMessagesReadEvent = ChatMessagesReadEvent.of(
                1L, 2L, new ChatReadResponse(10L, 1L, 20L)
        );
        redisChatEventPublisher.publishMessagesRead(chatMessagesReadEvent);
        assertParticipantDeliveries(firstServerMessagingTemplate, ChatSocketEventResponse.from(chatMessagesReadEvent));
        assertParticipantDeliveries(secondServerMessagingTemplate, ChatSocketEventResponse.from(chatMessagesReadEvent));

        verify(firstServerMessagingTemplate, after(200).times(2)).convertAndSendToUser(anyString(), eq("/queue/chat"), any());
        verify(secondServerMessagingTemplate, after(200).times(2)).convertAndSendToUser(anyString(), eq("/queue/chat"), any());
    }

    private RedisMessageListenerContainer startServerSubscriber(SimpMessagingTemplate messagingTemplate) {
        RedisChatEventSubscriber redisChatEventSubscriber = new RedisChatEventSubscriber(
                objectMapper, chatRedisProperties, new ChatSocketEventSender(messagingTemplate)
        );
        RedisMessageListenerContainer listenerContainer = new ChatRedisConfiguration().chatRedisMessageListenerContainer(
                redisConnectionFactory, redisChatEventSubscriber, chatRedisProperties
        );
        listenerContainer.afterPropertiesSet();
        listenerContainer.start();

        return listenerContainer;
    }

    private void assertParticipantDeliveries(
            SimpMessagingTemplate messagingTemplate,
            ChatSocketEventResponse<?> chatSocketEventResponse
    ) {
        verify(messagingTemplate, timeout(3000)).convertAndSendToUser("1", "/queue/chat", chatSocketEventResponse);
        verify(messagingTemplate, timeout(3000)).convertAndSendToUser("2", "/queue/chat", chatSocketEventResponse);
        verify(messagingTemplate, after(200).times(2)).convertAndSendToUser(anyString(), eq("/queue/chat"), any());
    }
}
