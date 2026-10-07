package com.anabada.fleaflea.domain.chat.event;

import com.anabada.fleaflea.domain.chat.dto.ChatMessageResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatSocketEventResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatTypingResponse;
import com.anabada.fleaflea.fixture.ChatFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class ChatEventListenerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatEventListener chatEventListener;

    @Test
    @DisplayName("입력 상태는 상대방에게만 전달한다")
    void onChatTypingChangedEvent_deliversOnlyToOtherMember() {
        ChatTypingResponse chatTypingResponse = ChatTypingResponse.from(ChatFixture.createChatRoomWithId(10L, 1L, 2L), 1L, true);
        ChatTypingChangedEvent chatTypingChangedEvent = ChatTypingChangedEvent.of(1L, 2L, chatTypingResponse);

        chatEventListener.onChatTypingChangedEvent(chatTypingChangedEvent);

        verify(messagingTemplate).convertAndSendToUser("2", "/queue/chat", ChatSocketEventResponse.from(chatTypingChangedEvent));
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("한 참여자에게 이벤트 전달이 실패해도 다른 참여자에게 전달한다")
    void onChatMessageSentEvent_continuesAfterDeliveryFailure() {
        ChatMessageResponse chatMessageResponse = ChatMessageResponse.from(ChatFixture.createChatMessageWithId(
                20L, 10L, 1L, ChatFixture.createChatMessageSendRequest("전달 확인"), LocalDateTime.of(2026, 1, 1, 12, 0)
        ));
        ChatMessageSentEvent chatMessageSentEvent = ChatMessageSentEvent.of(1L, 2L, chatMessageResponse);
        ChatSocketEventResponse<ChatMessageResponse> chatSocketEventResponse =
                ChatSocketEventResponse.from(chatMessageSentEvent);
        doThrow(new MessageDeliveryException("전달 실패"))
                .when(messagingTemplate).convertAndSendToUser("1", "/queue/chat", chatSocketEventResponse);

        chatEventListener.onChatMessageSentEvent(chatMessageSentEvent);

        verify(messagingTemplate).convertAndSendToUser("2", "/queue/chat", chatSocketEventResponse);
    }
}
