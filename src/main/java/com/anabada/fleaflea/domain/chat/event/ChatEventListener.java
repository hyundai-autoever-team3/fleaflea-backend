package com.anabada.fleaflea.domain.chat.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatEventListener {

    private final ChatEventPublisher chatEventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatMessageSentEvent(ChatMessageSentEvent chatMessageSentEvent) {
        chatEventPublisher.publishMessageSent(chatMessageSentEvent);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatMessagesReadEvent(ChatMessagesReadEvent chatMessagesReadEvent) {
        chatEventPublisher.publishMessagesRead(chatMessagesReadEvent);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatTypingChangedEvent(ChatTypingChangedEvent chatTypingChangedEvent) {
        chatEventPublisher.publishTypingChanged(chatTypingChangedEvent);
    }
}
