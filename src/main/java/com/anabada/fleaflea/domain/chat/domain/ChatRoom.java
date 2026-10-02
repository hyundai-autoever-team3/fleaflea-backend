package com.anabada.fleaflea.domain.chat.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "chat_rooms", uniqueConstraints = @UniqueConstraint(columnNames = {"member_low_id", "member_high_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long memberLowId;
    @Column(nullable = false) private Long memberHighId;
    @Column(nullable = false) private Long lowLastReadId = 0L;
    @Column(nullable = false) private Long highLastReadId = 0L;
    private Long lastMessageId;
    @Column(length = 2000) private String lastMessageContent;
    private LocalDateTime lastMessageAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    @Column(nullable = false) private LocalDateTime createdAt;

    public static ChatRoom create(Long a, Long b) {
        ChatRoom room = new ChatRoom();
        room.memberLowId = Math.min(a, b);
        room.memberHighId = Math.max(a, b);
        room.createdAt = room.updatedAt = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        return room;
    }

    public boolean hasMember(Long memberId) {
        return memberLowId.equals(memberId) || memberHighId.equals(memberId);
    }

    public Long otherMemberId(Long memberId) {
        return memberLowId.equals(memberId) ? memberHighId : memberLowId;
    }

    public long lastReadId(Long memberId) {
        return memberLowId.equals(memberId) ? lowLastReadId : highLastReadId;
    }

    public void read(Long memberId, long messageId) {
        if (memberLowId.equals(memberId)) lowLastReadId = Math.max(lowLastReadId, messageId);
        else highLastReadId = Math.max(highLastReadId, messageId);
    }

    public void recordMessage(ChatMessage message) {
        lastMessageId = message.getId();
        lastMessageContent = message.getContent();
        lastMessageAt = updatedAt = message.getCreatedAt();
    }
}
