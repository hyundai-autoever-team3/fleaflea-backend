package com.anabada.fleaflea.domain.notification.listener;

import com.anabada.fleaflea.domain.notification.domain.NotificationReferenceType;
import com.anabada.fleaflea.domain.notification.domain.NotificationType;
import com.anabada.fleaflea.domain.notification.service.NotificationMessageFactory;
import com.anabada.fleaflea.domain.notification.service.NotificationService;
import com.anabada.fleaflea.domain.trade.event.TradeAcceptedEvent;
import com.anabada.fleaflea.domain.trade.event.TradeCancelledEvent;
import com.anabada.fleaflea.domain.trade.event.TradeCompletedEvent;
import com.anabada.fleaflea.domain.trade.event.TradeKind;
import com.anabada.fleaflea.domain.trade.event.TradeRejectedEvent;
import com.anabada.fleaflea.domain.trade.event.TradeRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class TradeNotificationListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    @Async("tradeNotificationExecutor")
    public void handle(
            TradeRequestedEvent event
    ) {
        notificationService.createNotification(
                event.counterpartyId(),
                NotificationType.TRADE_REQUESTED,
                referenceType(event.target().kind()),
                event.requestId(),
                NotificationMessageFactory.tradeRequested(
                        event.requesterNickname(),
                        event.target().name()
                )
        );
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    @Async("tradeNotificationExecutor")
    public void handle(
            TradeAcceptedEvent event
    ) {
        notificationService.createNotification(
                event.requesterId(),
                NotificationType.TRADE_ACCEPTED,
                referenceType(event.target().kind()),
                event.requestId(),
                NotificationMessageFactory.tradeAccepted(
                        event.target().name()
                )
        );
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    @Async("tradeNotificationExecutor")
    public void handle(
            TradeRejectedEvent event
    ) {
        notificationService.createNotification(
                event.requesterId(),
                NotificationType.TRADE_REJECTED,
                referenceType(event.target().kind()),
                event.requestId(),
                NotificationMessageFactory.tradeRejected(
                        event.target().name()
                )
        );
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    @Async("tradeNotificationExecutor")
    public void handle(
            TradeCancelledEvent event
    ) {
        notificationService.createNotification(
                event.counterpartyId(),
                NotificationType.TRADE_CANCELLED,
                referenceType(event.target().kind()),
                event.requestId(),
                NotificationMessageFactory.tradeCancelled(
                        event.requesterNickname(),
                        event.target().name()
                )
        );
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    @Async("tradeNotificationExecutor")
    public void handle(
            TradeCompletedEvent event
    ) {
        notificationService.createNotification(
                event.counterpartyId(),
                NotificationType.TRADE_COMPLETED,
                referenceType(event.target().kind()),
                event.requestId(),
                NotificationMessageFactory.tradeCompleted(
                        event.confirmerNickname(),
                        event.target().name()
                )
        );
    }

    private NotificationReferenceType referenceType(
            TradeKind kind
    ) {
        return switch (kind) {
            case ITEM ->
                    NotificationReferenceType.ITEM_TRADE_REQUEST;

            case COLLECTION ->
                    NotificationReferenceType.COLLECTION_TRADE_REQUEST;
        };
    }
}
