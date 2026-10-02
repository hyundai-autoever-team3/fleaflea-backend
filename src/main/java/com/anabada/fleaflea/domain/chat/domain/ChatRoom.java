package com.anabada.fleaflea.domain.chat.domain;

import com.anabada.fleaflea.domain.chat.exception.ChatNotParticipantException;
import com.anabada.fleaflea.global.entity.BaseTimeEntity;
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

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "chat_rooms",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_low_id", "member_high_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberLowId;

    @Column(nullable = false)
    private Long memberHighId;

    @Column(nullable = false)
    private Long lowLastReadId = 0L;

    @Column(nullable = false)
    private Long highLastReadId = 0L;

    private Long lastMessageId;

    @Column(length = 2000)
    private String lastMessageContent;

    private LocalDateTime lastMessageAt;

    @Builder
    private ChatRoom(
            Long memberLowId,
            Long memberHighId
    ) {
        this.memberLowId = memberLowId;
        this.memberHighId = memberHighId;
    }

    public static ChatRoom create(Long memberId, Long friendId) {
        return ChatRoom.builder()
                .memberLowId(Math.min(memberId, friendId))
                .memberHighId(Math.max(memberId, friendId))
                .build();
    }

    public boolean isParticipant(Long memberId) {
        return memberLowId.equals(memberId) || memberHighId.equals(memberId);
    }

    public Long getOtherMemberId(Long memberId) {
        validateParticipant(memberId);

        return memberLowId.equals(memberId) ? memberHighId : memberLowId;
    }

    public long getLastReadMessageId(Long memberId) {
        validateParticipant(memberId);

        return memberLowId.equals(memberId) ? lowLastReadId : highLastReadId;
    }

    public void markMessagesAsRead(Long memberId, long messageId) {
        validateParticipant(memberId);

        if (memberLowId.equals(memberId)) {
            lowLastReadId = Math.max(lowLastReadId, messageId);
        } else {
            highLastReadId = Math.max(highLastReadId, messageId);
        }
    }

    public void recordMessage(ChatMessage message) {
        lastMessageId = message.getId();
        lastMessageContent = message.getContent();
        lastMessageAt = message.getCreatedAt();
    }

    private void validateParticipant(Long memberId) {
        if (!isParticipant(memberId)) {
            throw new ChatNotParticipantException();
        }
    }
}
