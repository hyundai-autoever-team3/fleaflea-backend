package com.anabada.fleaflea.domain.chat.event;

public interface ChatEventPublisher {

    void publishMessageSent(ChatMessageSentEvent chatMessageSentEvent);

    void publishMessagesRead(ChatMessagesReadEvent chatMessagesReadEvent);

    void publishTypingChanged(ChatTypingChangedEvent chatTypingChangedEvent);
}
