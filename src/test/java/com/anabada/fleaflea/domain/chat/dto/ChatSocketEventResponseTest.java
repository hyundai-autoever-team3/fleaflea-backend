package com.anabada.fleaflea.domain.chat.dto;

import com.anabada.fleaflea.domain.chat.domain.ChatRoom;
import com.anabada.fleaflea.domain.chat.event.ChatMessageSentEvent;
import com.anabada.fleaflea.domain.chat.event.ChatMessagesReadEvent;
import com.anabada.fleaflea.domain.chat.event.ChatTypingChangedEvent;
import com.anabada.fleaflea.fixture.ChatFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSocketEventResponseTest {

    @ParameterizedTest(name = "{index}: {1}")
    @MethodSource("socketEvents")
    @DisplayName("이벤트 타입을 분리해도 기존 소켓 이벤트 이름과 payload 필드를 유지한다")
    void serialize_preservesSocketContract(
            ChatSocketEventResponse<?> chatSocketEventResponse,
            String expectedType,
            String expectedPayloadField
    ) {
        JsonMapper jsonMapper = JsonMapper.builder().build();

        JsonNode json = jsonMapper.readTree(jsonMapper.writeValueAsString(chatSocketEventResponse));

        assertThat(json.get("type").asString()).isEqualTo(expectedType);
        assertThat(json.size()).isEqualTo(2);
        assertThat(json.get("payload").get(expectedPayloadField)).isNotNull();
        assertThat(json.get("payload").get("roomId").asLong()).isEqualTo(10L);
    }

    private static Stream<Arguments> socketEvents() {
        ChatRoom chatRoom = ChatFixture.createChatRoomWithId(10L, 1L, 2L);
        ChatMessageResponse chatMessageResponse = ChatMessageResponse.from(ChatFixture.createChatMessageWithId(
                20L,
                10L,
                1L,
                ChatFixture.createChatMessageSendRequest("직렬화 확인"),
                LocalDateTime.of(2026, 1, 1, 12, 0)
        ));
        ChatReadResponse chatReadResponse = ChatReadResponse.from(chatRoom, 1L);
        ChatTypingResponse chatTypingResponse = ChatTypingResponse.from(chatRoom, 1L, true);

        return Stream.of(
                Arguments.of(ChatSocketEventResponse.from(ChatMessageSentEvent.of(1L, 2L, chatMessageResponse)),
                        "chat-message", "content"),
                Arguments.of(ChatSocketEventResponse.from(ChatMessagesReadEvent.of(1L, 2L, chatReadResponse)),
                        "chat-read", "lastReadMessageId"),
                Arguments.of(ChatSocketEventResponse.from(ChatTypingChangedEvent.of(1L, 2L, chatTypingResponse)),
                        "chat-typing", "typing")
        );
    }
}
