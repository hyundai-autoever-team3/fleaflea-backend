package com.anabada.fleaflea.domain.chat.domain;

import com.anabada.fleaflea.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "chat_messages",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "sender_id", "client_message_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private Long senderId;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false, length = 36)
    private String clientMessageId;

    @Builder
    private ChatMessage(
            Long roomId,
            Long senderId,
            String content,
            String clientMessageId
    ) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.content = content;
        this.clientMessageId = clientMessageId;
    }

    public static ChatMessage create(
            Long roomId,
            Long senderId,
            String content,
            String clientMessageId
    ) {
        return ChatMessage.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content(content)
                .clientMessageId(clientMessageId)
                .build();
    }
}
