package com.anabada.fleaflea.domain.notification;

import com.anabada.fleaflea.domain.notification.domain.NotificationReferenceType;
import com.anabada.fleaflea.domain.notification.domain.NotificationType;
import com.anabada.fleaflea.domain.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class NotificationPropagationTest {

    @Autowired
    private NotificationService notificationService;

    @Test
    @DisplayName("트랜잭션 없이 알림을 생성하려 하면 실패한다")
    void createNotification_requiresExistingTransaction() {
        assertThatThrownBy(() ->
                notificationService.createNotification(
                        1L,
                        NotificationType.TRADE_REQUESTED,
                        NotificationReferenceType.ITEM_TRADE_REQUEST,
                        1L,
                        "트랜잭션 없이 호출"
                )
        ).isInstanceOf(IllegalTransactionStateException.class);
    }
}
