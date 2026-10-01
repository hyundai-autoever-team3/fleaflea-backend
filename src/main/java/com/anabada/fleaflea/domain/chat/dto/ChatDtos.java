package com.anabada.fleaflea.domain.chat.dto;

import com.anabada.fleaflea.domain.chat.domain.ChatMessage;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class ChatDtos {
    private ChatDtos() {}
    public record CreateRoom(@NotNull @Positive Long friendId) {}
    public record SendMessage(@NotBlank @Size(max = 2000) String content, @NotNull UUID clientMessageId) {}
    public record ReadMessage(@NotNull @Positive Long messageId) {}
    public record Message(Long id, Long roomId, Long senderId, String content, String clientMessageId, LocalDateTime createdAt) {
        public static Message from(ChatMessage m) {
            return new Message(m.getId(), m.getRoomId(), m.getSenderId(), m.getContent(), m.getClientMessageId(), m.getCreatedAt());
        }
    }
    public record Room(Long id, Long friendId, String nickname, String profileImageUrl, boolean canSend,
                       Long lastMessageId, String lastMessageContent, LocalDateTime lastMessageAt,
                       long myLastReadId, long friendLastReadId, long unreadCount) {}
    public record Rooms(List<Room> rooms, boolean hasNext) {}
    public record Messages(List<Message> messages, Long nextCursor, boolean hasNext) {}
    public record ReadReceipt(Long roomId, Long memberId, long lastReadMessageId) {}
}
