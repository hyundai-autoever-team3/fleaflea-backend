package com.anabada.fleaflea.domain.chat.redis;

import com.anabada.fleaflea.domain.chat.config.ChatRedisProperties;
import com.anabada.fleaflea.domain.chat.event.ChatEventType;
import com.anabada.fleaflea.domain.chat.event.ChatMessageSentEvent;
import com.anabada.fleaflea.domain.chat.event.ChatMessagesReadEvent;
import com.anabada.fleaflea.domain.chat.event.ChatSocketEventSender;
import com.anabada.fleaflea.domain.chat.event.ChatTypingChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.chat.redis", name = "enabled", havingValue = "true")
public class RedisChatEventSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final ChatRedisProperties chatRedisProperties;
    private final ChatSocketEventSender chatSocketEventSender;

    @Override
    public void onMessage(
            Message message,
            byte[] pattern
    ) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);

        try {
            if (channel.equals(chatRedisProperties.channelFor(ChatEventType.MESSAGE_SENT))) {
                ChatMessageSentEvent chatMessageSentEvent = objectMapper.readValue(message.getBody(), ChatMessageSentEvent.class);
                Objects.requireNonNull(chatMessageSentEvent.memberId());
                Objects.requireNonNull(chatMessageSentEvent.friendId());
                Objects.requireNonNull(chatMessageSentEvent.chatMessageResponse());
                chatSocketEventSender.sendMessageSent(chatMessageSentEvent);
            } else if (channel.equals(chatRedisProperties.channelFor(ChatEventType.MESSAGES_READ))) {
                ChatMessagesReadEvent chatMessagesReadEvent = objectMapper.readValue(message.getBody(), ChatMessagesReadEvent.class);
                Objects.requireNonNull(chatMessagesReadEvent.memberId());
                Objects.requireNonNull(chatMessagesReadEvent.friendId());
                Objects.requireNonNull(chatMessagesReadEvent.chatReadResponse());
                chatSocketEventSender.sendMessagesRead(chatMessagesReadEvent);
            } else if (channel.equals(chatRedisProperties.channelFor(ChatEventType.TYPING_CHANGED))) {
                ChatTypingChangedEvent chatTypingChangedEvent = objectMapper.readValue(message.getBody(), ChatTypingChangedEvent.class);
                Objects.requireNonNull(chatTypingChangedEvent.memberId());
                Objects.requireNonNull(chatTypingChangedEvent.friendId());
                Objects.requireNonNull(chatTypingChangedEvent.chatTypingResponse());
                chatSocketEventSender.sendTypingChanged(chatTypingChangedEvent);
            }
        } catch (RuntimeException exception) {
            log.warn("채팅 Redis 수신 이벤트 처리 실패: channel={}, cause={}", channel, exception.getClass().getSimpleName());
        }
    }
}
