package com.anabada.fleaflea.domain.notification.push;

import com.anabada.fleaflea.domain.notification.domain.PushCategory;

import com.anabada.fleaflea.domain.chat.dto.ChatMessageResponse;
import com.anabada.fleaflea.domain.notification.domain.NotificationReferenceType;
import com.anabada.fleaflea.domain.notification.domain.NotificationType;
import com.anabada.fleaflea.domain.notification.domain.PushSendStatus;
import com.anabada.fleaflea.domain.notification.dto.NotificationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PushMessageTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(NotificationType.class)
    @DisplayName("모든 일반 알림 유형의 식별자와 참조 정보를 푸시에 담는다")
    void notificationPayload_preservesExistingContract(NotificationType type) {
        PushMessage message = PushMessage.from(new NotificationResponse(10L, type,
                NotificationReferenceType.FRIENDSHIP, 7L, "알림 내용", false, LocalDateTime.now()));

        assertThat(message.title()).isEqualTo("Fleaflea");
        assertThat(message.body()).isEqualTo("알림 내용");
        assertThat(message.category()).isEqualTo(PushCategory.NOTIFICATION);
        assertThat(message.data()).isInstanceOfSatisfying(NotificationPushData.class, data -> {
            assertThat(data.notificationId()).isEqualTo(10L);
            assertThat(data.type()).isEqualTo(type);
            assertThat(data.referenceType()).isEqualTo(NotificationReferenceType.FRIENDSHIP);
            assertThat(data.referenceId()).isEqualTo(7L);
        });
        assertThat(message.toString()).doesNotContain("알림 내용");
    }

    @Test
    @DisplayName("채팅 미리보기는 줄바꿈과 유니코드 공백을 정리하며 전체 내용을 data에 넣지 않는다")
    void chatPreview_normalizesWhitespace() {
        PushMessage message = chat("  안녕\n\t\u00a0하세요  ");

        assertThat(message.title()).isEqualTo("민지");
        assertThat(message.body()).isEqualTo("안녕 하세요");
        assertThat(message.category()).isEqualTo(PushCategory.CHAT_MESSAGE);
        assertThat(message.data()).isEqualTo(ChatMessagePushData.of(3L, 2L));
    }

    @Test
    @DisplayName("100 코드 포인트까지 이모지를 자르지 않고 초과한 내용에만 말줄임표를 붙인다")
    void chatPreview_truncatesAtCodePointBoundary() {
        String emojis = "😀".repeat(100);

        assertThat(chat(emojis).body()).isEqualTo(emojis);
        assertThat(chat(emojis + "다음").body()).isEqualTo(emojis + "…");
    }

    private PushMessage chat(String content) {
        return PushMessage.of("민지", new ChatMessageResponse(2L, 3L, 1L, content, "client-id", LocalDateTime.now()));
    }

    @Test
    @DisplayName("푸시 record의 필수 값이 누락되면 전송 전에 생성을 거부한다")
    void missingData_rejectsCreation() {
        assertThatNullPointerException().isThrownBy(() -> ChatMessagePushData.of(null, 2L));
        assertThatNullPointerException().isThrownBy(() -> ChatMessagePushData.of(1L, null));
        assertThatNullPointerException().isThrownBy(() -> PushMessage.of("title", "body", null));
        assertThatNullPointerException().isThrownBy(() -> PushMessage.of(null, "body", ChatMessagePushData.of(1L, 2L)))
                .withMessage("title");
        assertThatNullPointerException().isThrownBy(() -> PushMessage.of("title", null, ChatMessagePushData.of(1L, 2L)))
                .withMessage("body");
        assertThatNullPointerException().isThrownBy(() -> PushSendResult.of(null, null))
                .withMessage("status");
        assertThat(PushSendResult.of(PushSendStatus.ACCEPTED, null).errorCode()).isNull();

        UUID registrationVersion = UUID.randomUUID();

        assertThatNullPointerException().isThrownBy(() -> PushTarget.of(null, 1L, "fid", registrationVersion))
                .withMessage("pushDeviceId");
        assertThatNullPointerException().isThrownBy(() -> PushTarget.of(1L, null, "fid", registrationVersion))
                .withMessage("memberId");
        assertThatNullPointerException().isThrownBy(() -> PushTarget.of(1L, 1L, null, registrationVersion))
                .withMessage("fid");
        assertThatNullPointerException().isThrownBy(() -> PushTarget.of(1L, 1L, "fid", null))
                .withMessage("registrationVersion");
        assertThatNullPointerException().isThrownBy(() -> NotificationPushData.of(
                null, NotificationType.POKE_RECEIVED, NotificationReferenceType.MEMBER_POKE, 1L));
        assertThatNullPointerException().isThrownBy(() -> NotificationPushData.of(
                1L, null, NotificationReferenceType.MEMBER_POKE, 1L));
        assertThatNullPointerException().isThrownBy(() -> NotificationPushData.of(
                1L, NotificationType.POKE_RECEIVED, null, 1L));
        assertThatNullPointerException().isThrownBy(() -> NotificationPushData.of(
                1L, NotificationType.POKE_RECEIVED, NotificationReferenceType.MEMBER_POKE, null));
    }
}
