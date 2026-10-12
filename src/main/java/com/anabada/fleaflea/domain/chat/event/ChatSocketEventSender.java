package com.anabada.fleaflea.domain.chat.event;

import com.anabada.fleaflea.domain.chat.dto.ChatMessageResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatReadResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatSocketEventResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSocketEventSender {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendMessageSent(ChatMessageSentEvent chatMessageSentEvent) {
        ChatSocketEventResponse<ChatMessageResponse> chatSocketEventResponse =
                ChatSocketEventResponse.from(chatMessageSentEvent);
        sendToMember(chatMessageSentEvent.memberId(), chatSocketEventResponse);
        sendToMember(chatMessageSentEvent.friendId(), chatSocketEventResponse);
    }

    public void sendMessagesRead(ChatMessagesReadEvent chatMessagesReadEvent) {
        ChatSocketEventResponse<ChatReadResponse> chatSocketEventResponse =
                ChatSocketEventResponse.from(chatMessagesReadEvent);
        sendToMember(chatMessagesReadEvent.memberId(), chatSocketEventResponse);
        sendToMember(chatMessagesReadEvent.friendId(), chatSocketEventResponse);
    }

    public void sendTypingChanged(ChatTypingChangedEvent chatTypingChangedEvent) {
        sendToMember(chatTypingChangedEvent.friendId(), ChatSocketEventResponse.from(chatTypingChangedEvent));
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
