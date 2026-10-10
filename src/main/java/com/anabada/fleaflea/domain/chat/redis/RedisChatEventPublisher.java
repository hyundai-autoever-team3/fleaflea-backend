package com.anabada.fleaflea.domain.chat.redis;

import com.anabada.fleaflea.domain.chat.config.ChatRedisProperties;
import com.anabada.fleaflea.domain.chat.event.ChatEventPublisher;
import com.anabada.fleaflea.domain.chat.event.ChatEventType;
import com.anabada.fleaflea.domain.chat.event.ChatMessageSentEvent;
import com.anabada.fleaflea.domain.chat.event.ChatMessagesReadEvent;
import com.anabada.fleaflea.domain.chat.event.ChatTypingChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.chat.redis", name = "enabled", havingValue = "true")
public class RedisChatEventPublisher implements ChatEventPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ChatRedisProperties chatRedisProperties;

    @Override
    public void publishMessageSent(ChatMessageSentEvent chatMessageSentEvent) {
        publishEvent(ChatEventType.MESSAGE_SENT, () -> objectMapper.writeValueAsString(chatMessageSentEvent));
    }

    @Override
    public void publishMessagesRead(ChatMessagesReadEvent chatMessagesReadEvent) {
        publishEvent(ChatEventType.MESSAGES_READ, () -> objectMapper.writeValueAsString(chatMessagesReadEvent));
    }

    @Override
    public void publishTypingChanged(ChatTypingChangedEvent chatTypingChangedEvent) {
        publishEvent(ChatEventType.TYPING_CHANGED, () -> objectMapper.writeValueAsString(chatTypingChangedEvent));
    }

    private void publishEvent(
            ChatEventType chatEventType,
            Supplier<String> serializedEventSupplier
    ) {
        try {
            stringRedisTemplate.convertAndSend(chatRedisProperties.channelFor(chatEventType), serializedEventSupplier.get());
        } catch (DataAccessException | JacksonException exception) {
            // 저장은 이미 커밋됐으므로 전송 실패를 요청 실패로 바꾸지 않는다.
            log.warn("채팅 Redis 발행 실패: type={}", chatEventType, exception);
        }
    }
}
