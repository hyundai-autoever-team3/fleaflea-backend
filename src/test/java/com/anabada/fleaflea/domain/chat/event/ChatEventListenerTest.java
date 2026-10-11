package com.anabada.fleaflea.domain.chat.event;

import com.anabada.fleaflea.domain.chat.dto.ChatMessageResponse;
import com.anabada.fleaflea.domain.chat.dto.ChatTypingResponse;
import com.anabada.fleaflea.fixture.ChatFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class ChatEventListenerTest {

    @Mock
    private ChatEventPublisher chatEventPublisher;

    @InjectMocks
    private ChatEventListener chatEventListener;

    @Test
    @DisplayName("입력 상태 이벤트를 선택된 전송 방식으로 발행한다")
    void onChatTypingChangedEvent_publishesTypedEvent() {
        ChatTypingResponse chatTypingResponse = ChatTypingResponse.from(ChatFixture.createChatRoomWithId(10L, 1L, 2L), 1L, true);
        ChatTypingChangedEvent chatTypingChangedEvent = ChatTypingChangedEvent.of(1L, 2L, chatTypingResponse);

        chatEventListener.onChatTypingChangedEvent(chatTypingChangedEvent);

        verify(chatEventPublisher).publishTypingChanged(chatTypingChangedEvent);
        verifyNoMoreInteractions(chatEventPublisher);
    }

    @Test
    @DisplayName("메시지 이벤트를 선택된 전송 방식으로 한 번 발행한다")
    void onChatMessageSentEvent_publishesTypedEventOnce() {
        ChatMessageResponse chatMessageResponse = ChatMessageResponse.from(ChatFixture.createChatMessageWithId(
                20L, 10L, 1L, ChatFixture.createChatMessageSendRequest("전달 확인"), LocalDateTime.of(2026, 1, 1, 12, 0)
        ));
        ChatMessageSentEvent chatMessageSentEvent = ChatMessageSentEvent.of(1L, 2L, chatMessageResponse);

        chatEventListener.onChatMessageSentEvent(chatMessageSentEvent);

        verify(chatEventPublisher).publishMessageSent(chatMessageSentEvent);
        verifyNoMoreInteractions(chatEventPublisher);
    }
}
