package com.anabada.fleaflea.domain.chat.event;

import com.anabada.fleaflea.domain.chat.dto.ChatSocketEventResponse;
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
    public void onChatEvent(ChatEvent event) {
        ChatSocketEventResponse response = ChatSocketEventResponse.from(event);
        if (!ChatEvent.TYPING_CHANGED.equals(event.eventName())) {
            sendToMember(event.firstMemberId(), response);
        }
        sendToMember(event.secondMemberId(), response);
    }

    private void sendToMember(Long memberId, ChatSocketEventResponse response) {
        try {
            messagingTemplate.convertAndSendToUser(memberId.toString(), "/queue/chat", response);
        } catch (MessagingException exception) {
            log.warn("채팅 이벤트 전달 실패: memberId={}, type={}", memberId, response.type(), exception);
        }
    }
}
