package com.anabada.fleaflea.domain.chat.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "chat_messages", uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "sender_id", "client_message_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long roomId;
    @Column(nullable = false) private Long senderId;
    @Column(nullable = false, length = 2000) private String content;
    @Column(nullable = false, length = 36) private String clientMessageId;
    @Column(nullable = false) private LocalDateTime createdAt;

    public static ChatMessage create(Long roomId, Long senderId, String content, String clientMessageId) {
        ChatMessage message = new ChatMessage();
        message.roomId = roomId;
        message.senderId = senderId;
        message.content = content;
        message.clientMessageId = clientMessageId;
        message.createdAt = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        return message;
    }
}
