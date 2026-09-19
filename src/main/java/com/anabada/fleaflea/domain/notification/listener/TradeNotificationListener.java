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
                requestedMessage(event)
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
                acceptedMessage(event)
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
                rejectedMessage(event)
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
                cancelledMessage(event)
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
                completedMessage(event)
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

            case BEG ->
                    NotificationReferenceType.BEG_REQUEST;
        };
    }

    private String requestedMessage(
            TradeRequestedEvent event
    ) {
        return switch (event.target().kind()) {
            case ITEM, COLLECTION ->
                    NotificationMessageFactory.tradeRequested(
                            event.requesterNickname(),
                            event.target().name()
                    );

            case BEG ->
                    NotificationMessageFactory.begRequested(
                            event.requesterNickname(),
                            event.target().name()
                    );
        };
    }

    private String acceptedMessage(
            TradeAcceptedEvent event
    ) {
        return switch (event.target().kind()) {
            case ITEM, COLLECTION ->
                    NotificationMessageFactory.tradeAccepted(
                            event.target().name()
                    );

            case BEG ->
                    NotificationMessageFactory.begAccepted(
                            event.target().name()
                    );
        };
    }

    private String rejectedMessage(
            TradeRejectedEvent event
    ) {
        return switch (event.target().kind()) {
            case ITEM, COLLECTION ->
                    NotificationMessageFactory.tradeRejected(
                            event.target().name()
                    );

            case BEG ->
                    NotificationMessageFactory.begRejected(
                            event.target().name()
                    );
        };
    }

    private String cancelledMessage(
            TradeCancelledEvent event
    ) {
        return switch (event.target().kind()) {
            case ITEM, COLLECTION ->
                    NotificationMessageFactory.tradeCancelled(
                            event.requesterNickname(),
                            event.target().name()
                    );

            case BEG ->
                    NotificationMessageFactory.begCancelled(
                            event.requesterNickname(),
                            event.target().name()
                    );
        };
    }

    private String completedMessage(
            TradeCompletedEvent event
    ) {
        return switch (event.target().kind()) {
            case ITEM, COLLECTION ->
                    NotificationMessageFactory.tradeCompleted(
                            event.confirmerNickname(),
                            event.target().name()
                    );

            case BEG ->
                    NotificationMessageFactory.begCompleted(
                            event.target().name()
                    );
        };
    }
}
