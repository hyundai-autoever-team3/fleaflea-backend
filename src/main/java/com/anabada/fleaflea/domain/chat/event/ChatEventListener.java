package com.anabada.fleaflea.domain.chat.event;

import com.anabada.fleaflea.domain.chat.dto.ChatMessageResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatReadResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatSocketEventResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatTypingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatMessageSentEvent(ChatMessageSentEvent chatMessageSentEvent) {
        ChatSocketEventResponse<ChatMessageResponse> chatSocketEventResponse =
                ChatSocketEventResponse.from(chatMessageSentEvent);
        sendToMember(chatMessageSentEvent.memberId(), chatSocketEventResponse);
        sendToMember(chatMessageSentEvent.friendId(), chatSocketEventResponse);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatMessagesReadEvent(ChatMessagesReadEvent chatMessagesReadEvent) {
        ChatSocketEventResponse<ChatReadResponse> chatSocketEventResponse =
                ChatSocketEventResponse.from(chatMessagesReadEvent);
        sendToMember(chatMessagesReadEvent.memberId(), chatSocketEventResponse);
        sendToMember(chatMessagesReadEvent.friendId(), chatSocketEventResponse);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatTypingChangedEvent(ChatTypingChangedEvent chatTypingChangedEvent) {
        ChatSocketEventResponse<ChatTypingResponse> chatSocketEventResponse =
                ChatSocketEventResponse.from(chatTypingChangedEvent);
        sendToMember(chatTypingChangedEvent.friendId(), chatSocketEventResponse);
    }

    private void sendToMember(
            Long memberId,
            ChatSocketEventResponse<?> chatSocketEventResponse
    ) {
        try {
            messagingTemplate.convertAndSendToUser(memberId.toString(), "/queue/chat", chatSocketEventResponse);
        } catch (MessagingException exception) {
            log.warn("채팅 이벤트 전달 실패: memberId={}, type={}", memberId, chatSocketEventResponse.type(), exception);
        }
    }
}
