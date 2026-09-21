package com.anabada.fleaflea.domain.notification;

import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.notification.domain.NotificationReferenceType;
import com.anabada.fleaflea.domain.notification.domain.NotificationType;
import com.anabada.fleaflea.domain.notification.service.NotificationService;
import com.anabada.fleaflea.fixture.MemberFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@Transactional
class NotificationTypeConstraintTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("모든 NotificationType이 DB CHECK 제약을 통과한다")
    void allNotificationTypes_satisfyCheckConstraint() {
        Member receiver = memberRepository.save(MemberFixture.createMember("receiver"));

        for (NotificationType type : NotificationType.values()) {
            assertThatCode(() -> {
                notificationService.createNotification(
                        receiver.getMemberId(),
                        type,
                        NotificationReferenceType.ITEM_TRADE_REQUEST,
                        1L,
                        "제약 검증용 메시지"
                );
                entityManager.flush();
            })
                    .as("NotificationType.%s 저장 실패 - 마이그레이션이 누락됐을 수 있다", type)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("모든 NotificationReferenceType이 DB CHECK 제약을 통과한다")
    void allReferenceTypes_satisfyCheckConstraint() {
        Member receiver = memberRepository.save(MemberFixture.createMember("receiver"));

        for (NotificationReferenceType referenceType : NotificationReferenceType.values()) {
            assertThatCode(() -> {
                notificationService.createNotification(
                        receiver.getMemberId(),
                        NotificationType.TRADE_REQUESTED,
                        referenceType,
                        1L,
                        "제약 검증용 메시지"
                );
                entityManager.flush();
            })
                    .as("NotificationReferenceType.%s 저장 실패 - 마이그레이션이 누락됐을 수 있다", referenceType)
                    .doesNotThrowAnyException();
        }
    }
}
