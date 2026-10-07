package com.anabada.fleaflea.fixture;

import com.anabada.fleaflea.domain.chat.domain.ChatMessage;
import com.anabada.fleaflea.domain.chat.domain.ChatRoom;
import com.anabada.fleaflea.domain.chat.dto.ChatMessageSendRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChatFixture {

    public static ChatRoom createChatRoom(
            Long memberId,
            Long friendId
    ) {
        return ChatRoom.create(memberId, friendId);
    }

    public static ChatRoom createChatRoomWithId(
            Long roomId,
            Long memberId,
            Long friendId
    ) {
        ChatRoom chatRoom = createChatRoom(memberId, friendId);
        ReflectionTestUtils.setField(chatRoom, "id", roomId);

        return chatRoom;
    }

    public static ChatMessageSendRequest createChatMessageSendRequest(String content) {
        return createChatMessageSendRequest(content, UUID.randomUUID());
    }

    public static ChatMessageSendRequest createChatMessageSendRequest(
            String content,
            UUID clientMessageId
    ) {
        return new ChatMessageSendRequest(content, clientMessageId);
    }

    public static ChatMessage createChatMessage(
            Long roomId,
            Long senderId,
            String content
    ) {
        return ChatMessage.create(roomId, senderId, content, UUID.randomUUID().toString());
    }

    public static ChatMessage createChatMessageWithId(
            Long messageId,
            Long roomId,
            Long senderId,
            ChatMessageSendRequest chatMessageSendRequest,
            LocalDateTime createdAt
    ) {
        ChatMessage chatMessage = ChatMessage.create(
                roomId,
                senderId,
                chatMessageSendRequest.content(),
                chatMessageSendRequest.clientMessageId().toString()
        );
        ReflectionTestUtils.setField(chatMessage, "id", messageId);
        ReflectionTestUtils.setField(chatMessage, "createdAt", createdAt);

        return chatMessage;
    }
}
