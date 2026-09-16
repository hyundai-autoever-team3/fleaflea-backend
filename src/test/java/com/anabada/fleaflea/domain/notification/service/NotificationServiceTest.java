package com.anabada.fleaflea.domain.notification.service;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.notification.domain.Notification;
import com.anabada.fleaflea.domain.notification.dto.NotificationUnreadCountResponse;
import com.anabada.fleaflea.domain.notification.exception.NotificationNotFoundException;
import com.anabada.fleaflea.domain.notification.exception.NotificationNotReceiverException;
import com.anabada.fleaflea.domain.notification.repository.NotificationRepository;
import com.anabada.fleaflea.fixture.MemberFixture;
import com.anabada.fleaflea.fixture.NotificationFixture;
import com.anabada.fleaflea.global.dto.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("알림 목록을 페이지네이션하여 조회한다")
    void getNotifications() {
        Member receiver = MemberFixture.createMember(1L);
        Notification notification = NotificationFixture.createNotification(receiver);

        Page<Notification> page = new PageImpl<>(
                List.of(notification),
                PageRequest.of(0, 10),
                1
        );

        when(notificationRepository.findAllByReceiver_MemberId(
                eq(1L),
                any(Pageable.class)
        )).thenReturn(page);

        PageResponse<?> response =
                notificationService.getNotifications(1L, 0, 10);

        assertThat(response.content()).hasSize(1);
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(10);

        verify(notificationRepository)
                .findAllByReceiver_MemberId(eq(1L), any(Pageable.class));
    }

    @Test
    @DisplayName("미확인 알림 개수를 조회한다")
    void getUnreadCount() {
        when(notificationRepository
                .countByReceiver_MemberIdAndIsReadFalse(1L))
                .thenReturn(3L);

        NotificationUnreadCountResponse response =
                notificationService.getUnreadCount(1L);

        assertThat(response.unreadCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("알림을 읽음 처리한다")
    void readNotification() {
        Member receiver = MemberFixture.createMember(1L);
        Notification notification = NotificationFixture.createNotification(receiver);

        when(notificationRepository.findById(1L))
                .thenReturn(Optional.of(notification));

        notificationService.readNotification(1L, 1L);

        assertThat(notification.isRead()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 알림 읽음 처리 시 예외가 발생한다")
    void readNotification_notFound() {
        when(notificationRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                notificationService.readNotification(1L, 1L)
        ).isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    @DisplayName("알림 수신자가 아닌 회원이 읽음 처리하면 예외가 발생한다")
    void readNotification_notReceiver() {
        Member receiver = MemberFixture.createMember(1L);
        Notification notification = NotificationFixture.createNotification(receiver);

        when(notificationRepository.findById(1L))
                .thenReturn(Optional.of(notification));

        assertThatThrownBy(() ->
                notificationService.readNotification(1L, 2L)
        ).isInstanceOf(NotificationNotReceiverException.class);
    }

    @Test
    @DisplayName("모든 알림을 읽음 처리한다")
    void readAllNotifications() {
        notificationService.readAllNotifications(1L);

        verify(notificationRepository)
                .readAllByMemberId(1L);
    }

    @Test
    @DisplayName("알림을 삭제한다")
    void deleteNotification() {
        Member receiver = MemberFixture.createMember(1L);
        Notification notification = NotificationFixture.createNotification(receiver);

        when(notificationRepository.findById(1L))
                .thenReturn(Optional.of(notification));

        notificationService.deleteNotification(1L, 1L);

        verify(notificationRepository)
                .delete(notification);
    }

    @Test
    @DisplayName("수신자가 아닌 회원이 알림 삭제 시 예외가 발생한다")
    void deleteNotification_notReceiver() {
        Member receiver = MemberFixture.createMember(1L);
        Notification notification = NotificationFixture.createNotification(receiver);

        when(notificationRepository.findById(1L))
                .thenReturn(Optional.of(notification));

        assertThatThrownBy(() ->
                notificationService.deleteNotification(1L, 2L)
        ).isInstanceOf(NotificationNotReceiverException.class);

        verify(notificationRepository, never())
                .delete(any(Notification.class));
    }

    @Test
    @DisplayName("모든 알림을 삭제한다")
    void deleteAllNotifications() {
        notificationService.deleteAllNotifications(1L);

        verify(notificationRepository)
                .deleteAllByMemberId(1L);
    }
}