package com.anabada.fleaflea.domain.chat.event;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.chat.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalChatEventPublisher implements ChatEventPublisher {

    private final ChatSocketEventSender chatSocketEventSender;

    @Override
    public void publishMessageSent(ChatMessageSentEvent chatMessageSentEvent) {
        chatSocketEventSender.sendMessageSent(chatMessageSentEvent);
    }

    @Override
    public void publishMessagesRead(ChatMessagesReadEvent chatMessagesReadEvent) {
        chatSocketEventSender.sendMessagesRead(chatMessagesReadEvent);
    }

    @Override
    public void publishTypingChanged(ChatTypingChangedEvent chatTypingChangedEvent) {
        chatSocketEventSender.sendTypingChanged(chatTypingChangedEvent);
    }
}
