package com.anabada.fleaflea.domain.notification;

import com.anabada.fleaflea.domain.notification.domain.PushCategory;

import com.anabada.fleaflea.domain.notification.domain.PushSendStatus;
import com.anabada.fleaflea.domain.chat.dto.ChatMessageSendRequest;
import com.anabada.fleaflea.domain.chat.service.ChatService;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.notification.domain.*;
import com.anabada.fleaflea.domain.notification.push.*;
import com.anabada.fleaflea.domain.notification.repository.NotificationRepository;
import com.anabada.fleaflea.domain.notification.service.*;
import com.anabada.fleaflea.fixture.*;
import com.anabada.fleaflea.global.image.ImageService;
import com.anabada.fleaflea.support.PostgresIntegrationTest;
import com.anabada.fleaflea.support.RedisTestContainerConfiguration;
import com.google.firebase.FirebaseApp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@PostgresIntegrationTest
@Import(RedisTestContainerConfiguration.class)
@TestPropertySource(properties = {"fcm.enabled=true", "fcm.project-id=test-push"})
@Sql(statements = "TRUNCATE TABLE chat_messages, chat_rooms, friendships, members RESTART IDENTITY CASCADE",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(statements = "TRUNCATE TABLE chat_messages, chat_rooms, friendships, members RESTART IDENTITY CASCADE",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PushEventIntegrationTest {

    @Autowired
    private MemberRepository members;

    @Autowired
    private FriendshipRepository friendships;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ChatService chat;

    @Autowired
    private PushDeviceService devices;

    @Autowired
    private PlatformTransactionManager manager;

    @MockitoBean
    private FirebasePushSender sender;

    @MockitoBean(name = "pushFirebaseApp")
    private FirebaseApp firebaseApp;

    @MockitoBean
    private ImageService imageService;

    private Member first;

    private Member second;

    @BeforeEach
    void setUp() {
        first = members.save(MemberFixture.createMember("sender"));
        second = members.save(MemberFixture.createMember("receiver"));
        friendships.save(FriendshipFixture.createFriendship(first, second, FriendshipStatus.ACCEPTED));
        devices.registerPushDevice(first.getMemberId(), "c1234567890123456789012", PushPlatform.WEB);
        devices.registerPushDevice(second.getMemberId(), "d1234567890123456789012", PushPlatform.WEB);
        when(sender.send(any(), any())).thenReturn(PushSendResult.of(PushSendStatus.ACCEPTED, null));
    }

    @Test
    @DisplayName("일반 알림은 커밋 전에 전송하지 않고 커밋 후 수신 기기에 전달한다")
    void notification_onlySendsAfterCommit() {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            createNotification();
            verifyNoInteractions(sender);
        });
        verify(sender, timeout(3000)).send(argThat(target -> target.memberId().equals(second.getMemberId())),
                argThat(message -> message.category() == PushCategory.NOTIFICATION
                        && message.title().equals("Fleaflea")
                        && message.body().equals("테스트 콕찌르기")));
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("일반 알림과 채팅 저장을 롤백하면 푸시를 전송하지 않는다")
    void rollback_doesNotSendNotificationOrChat() {
        Long roomId = chat.getOrCreateChatRoom(first.getMemberId(), second.getMemberId()).id();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            createNotification();
            chat.sendMessage(first.getMemberId(), roomId, request());
            status.setRollbackOnly();
        });

        verify(sender, after(400).never()).send(any(), any());
        assertThat(notifications.count()).isZero();
    }

    @Test
    @DisplayName("채팅 재전송은 상대방 푸시를 추가 생성하지 않고 일반 알림도 저장하지 않는다")
    void chat_retryOnlySendsOnceToReceiver() {
        Long roomId = chat.getOrCreateChatRoom(first.getMemberId(), second.getMemberId()).id();
        ChatMessageSendRequest request = request();
        var saved = new TransactionTemplate(manager).execute(status -> {
            var response = chat.sendMessage(first.getMemberId(), roomId, request);
            verifyNoInteractions(sender);
            return response;
        });

        assertThat(saved).isNotNull();
        assertThat(chat.sendMessage(first.getMemberId(), roomId, request)).isEqualTo(saved);
        verify(sender, timeout(3000)).send(argThat(target -> target.memberId().equals(second.getMemberId())),
                argThat(message -> message.title().equals(first.getNickname())
                        && message.body().equals(request.content())
                        && message.data() instanceof ChatMessagePushData data
                        && data.messageId().equals(saved.id())));

        chat.markMessagesAsRead(second.getMemberId(), roomId, saved.id());
        chat.updateTypingStatus(first.getMemberId(), roomId, true);

        verify(sender, after(400).times(1)).send(any(), any());
        assertThat(notifications.count()).isZero();
    }

    private void createNotification() {
        notificationService.createNotification(second.getMemberId(), NotificationType.POKE_RECEIVED,
                NotificationReferenceType.MEMBER_POKE, 1L, "테스트 콕찌르기");
    }

    private ChatMessageSendRequest request() {
        return new ChatMessageSendRequest("안녕하세요", UUID.randomUUID());
    }
}
