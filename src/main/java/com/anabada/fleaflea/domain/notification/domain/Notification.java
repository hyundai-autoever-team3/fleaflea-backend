package com.anabada.fleaflea.domain.notification.domain;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Member receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 30)
    private NotificationReferenceType referenceType;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(nullable = false, length = 255)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Builder
    private Notification(
            Member receiver,
            NotificationType type,
            NotificationReferenceType referenceType,
            Long referenceId,
            String message,
            boolean isRead
    ) {
        this.receiver = receiver;
        this.type = type;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.message = message;
        this.isRead = isRead;
    }

    public static Notification create(
            Member receiver,
            NotificationType type,
            NotificationReferenceType referenceType,
            Long referenceId,
            String message
    ) {
        return Notification.builder()
                .receiver(receiver)
                .type(type)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .message(message)
                .isRead(false)
                .build();
    }

    public void read() {
        this.isRead = true;
    }

    public boolean isReceiver(Long memberId) {
        return receiver.getMemberId().equals(memberId);
    }
}