package com.anabada.fleaflea.domain.notification.service;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.notification.domain.Notification;
import com.anabada.fleaflea.domain.notification.domain.NotificationReferenceType;
import com.anabada.fleaflea.domain.notification.domain.NotificationType;
import com.anabada.fleaflea.domain.notification.dto.NotificationResponse;
import com.anabada.fleaflea.domain.notification.dto.NotificationUnreadCountResponse;
import com.anabada.fleaflea.domain.notification.event.NotificationCreatedEvent;
import com.anabada.fleaflea.domain.notification.exception.NotificationNotFoundException;
import com.anabada.fleaflea.domain.notification.exception.NotificationNotReceiverException;
import com.anabada.fleaflea.domain.notification.repository.NotificationRepository;
import com.anabada.fleaflea.global.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 알림은 발행자의 트랜잭션과 함께 커밋되어야 한다
     * MANDATORY라 트랜잭션 밖에서 호출하면 즉시 실패
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void createNotification(
            Long receiverId,
            NotificationType type,
            NotificationReferenceType referenceType,
            Long referenceId,
            String message
    ) {
        Member receiver =
                memberRepository.getReferenceById(receiverId);

        Notification notification = Notification.create(
                receiver,
                type,
                referenceType,
                referenceId,
                message
        );

        notificationRepository.save(notification);

        eventPublisher.publishEvent(
                new NotificationCreatedEvent(
                        receiverId,
                        NotificationResponse.from(notification)
                )
        );
    }


    public PageResponse<NotificationResponse> getNotifications(
            Long memberId,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<NotificationResponse> responses = notificationRepository
                .findAllByReceiver_MemberId(memberId, pageable)
                .map(NotificationResponse::from);

        return PageResponse.from(responses);
    }

    public NotificationUnreadCountResponse getUnreadCount(Long memberId) {
        long unreadCount =
                notificationRepository.countByReceiver_MemberIdAndIsReadFalse(memberId);

        return new NotificationUnreadCountResponse(unreadCount);
    }

    @Transactional
    public void readNotification(
            Long notificationId,
            Long memberId
    ) {
        Notification notification = findNotificationOrThrow(notificationId);

        validateReceiver(notification, memberId);

        notification.read();
    }

    @Transactional
    public void readAllNotifications(Long memberId) {
        notificationRepository.readAllByMemberId(memberId);
    }

    @Transactional
    public void deleteNotification(
            Long notificationId,
            Long memberId
    ) {
        Notification notification = findNotificationOrThrow(notificationId);

        validateReceiver(notification, memberId);

        notificationRepository.delete(notification);
    }

    @Transactional
    public void deleteAllNotifications(Long memberId) {
        notificationRepository.deleteAllByMemberId(memberId);
    }

    private Notification findNotificationOrThrow(Long notificationId) {
        return notificationRepository.findById(notificationId)
                .orElseThrow(NotificationNotFoundException::new);
    }

    private void validateReceiver(
            Notification notification,
            Long memberId
    ) {
        if (!notification.isReceiver(memberId)) {
            throw new NotificationNotReceiverException();
        }
    }
}