package com.anabada.fleaflea.domain.notification.notifier;

import com.anabada.fleaflea.domain.notification.domain.NotificationReferenceType;
import com.anabada.fleaflea.domain.notification.domain.NotificationType;
import com.anabada.fleaflea.domain.notification.service.NotificationMessageFactory;
import com.anabada.fleaflea.domain.notification.service.NotificationService;
import com.anabada.fleaflea.domain.trade.event.TradeAcceptedEvent;
import com.anabada.fleaflea.domain.trade.event.TradeAutoRejectedEvent;
import com.anabada.fleaflea.domain.trade.event.TradeCancelledEvent;
import com.anabada.fleaflea.domain.trade.event.TradeCompletedEvent;
import com.anabada.fleaflea.domain.trade.event.TradeKind;
import com.anabada.fleaflea.domain.trade.event.TradeRejectedEvent;
import com.anabada.fleaflea.domain.trade.event.TradeRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TradeNotifier {

    private final NotificationService notificationService;

    public void notifyOf(
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

    public void notifyOf(
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

    public void notifyOf(
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

    public void notifyOf(
            TradeAutoRejectedEvent event
    ) {
        notificationService.createNotification(
                event.requesterId(),
                NotificationType.TRADE_REJECTED,
                referenceType(event.target().kind()),
                event.requestId(),
                autoRejectedMessage(event)
        );
    }

    public void notifyOf(
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

    public void notifyOf(
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
        return switch (event.target().dealType()) {
            case SALE ->
                    NotificationMessageFactory.saleRequested(
                            event.requesterNickname(),
                            event.target().name()
                    );

            case GIVEAWAY ->
                    NotificationMessageFactory.giveawayRequested(
                            event.requesterNickname(),
                            event.target().name()
                    );

            case ITEM_RENTAL ->
                    NotificationMessageFactory.itemRentalRequested(
                            event.requesterNickname(),
                            event.target().name()
                    );

            case COLLECTION_RENTAL ->
                    NotificationMessageFactory.collectionRentalRequested(
                            event.requesterNickname(),
                            event.target().name()
                    );

            case EXCHANGE ->
                    NotificationMessageFactory.exchangeRequested(
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
        return switch (event.target().dealType()) {
            case SALE ->
                    NotificationMessageFactory.saleAccepted(
                            event.target().name()
                    );

            case GIVEAWAY ->
                    NotificationMessageFactory.giveawayAccepted(
                            event.target().name()
                    );

            case ITEM_RENTAL ->
                    NotificationMessageFactory.itemRentalAccepted(
                            event.target().name()
                    );

            case COLLECTION_RENTAL ->
                    NotificationMessageFactory.collectionRentalAccepted(
                            event.target().name()
                    );

            case EXCHANGE ->
                    NotificationMessageFactory.exchangeAccepted(
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
        return switch (event.target().dealType()) {
            case SALE ->
                    NotificationMessageFactory.saleRejected(
                            event.target().name()
                    );

            case GIVEAWAY ->
                    NotificationMessageFactory.giveawayRejected(
                            event.target().name()
                    );

            case ITEM_RENTAL ->
                    NotificationMessageFactory.itemRentalRejected(
                            event.target().name()
                    );

            case COLLECTION_RENTAL ->
                    NotificationMessageFactory.collectionRentalRejected(
                            event.target().name()
                    );

            case EXCHANGE ->
                    NotificationMessageFactory.exchangeRejected(
                            event.target().name()
                    );

            case BEG ->
                    NotificationMessageFactory.begRejected(
                            event.target().name()
                    );
        };
    }

    private String autoRejectedMessage(
            TradeAutoRejectedEvent event
    ) {
        return switch (event.target().dealType()) {
            case SALE ->
                    NotificationMessageFactory.saleAutoRejected(
                            event.target().name()
                    );

            case GIVEAWAY ->
                    NotificationMessageFactory.giveawayAutoRejected(
                            event.target().name()
                    );

            case ITEM_RENTAL ->
                    NotificationMessageFactory.itemRentalAutoRejected(
                            event.target().name()
                    );

            case COLLECTION_RENTAL ->
                    NotificationMessageFactory.collectionRentalAutoRejected(
                            event.target().name()
                    );

            case EXCHANGE ->
                    NotificationMessageFactory.exchangeAutoRejected(
                            event.target().name()
                    );

            case BEG ->
                    NotificationMessageFactory.begAutoRejected(
                            event.target().name()
                    );
        };
    }

    private String cancelledMessage(
            TradeCancelledEvent event
    ) {
        return switch (event.target().dealType()) {
            case SALE ->
                    NotificationMessageFactory.saleCancelled(
                            event.requesterNickname(),
                            event.target().name()
                    );

            case GIVEAWAY ->
                    NotificationMessageFactory.giveawayCancelled(
                            event.requesterNickname(),
                            event.target().name()
                    );

            case ITEM_RENTAL ->
                    NotificationMessageFactory.itemRentalCancelled(
                            event.requesterNickname(),
                            event.target().name()
                    );

            case COLLECTION_RENTAL ->
                    NotificationMessageFactory.collectionRentalCancelled(
                            event.requesterNickname(),
                            event.target().name()
                    );

            case EXCHANGE ->
                    NotificationMessageFactory.exchangeCancelled(
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
        return switch (event.target().dealType()) {
            case SALE ->
                    NotificationMessageFactory.saleCompleted(
                            event.confirmerNickname(),
                            event.target().name()
                    );

            case GIVEAWAY ->
                    NotificationMessageFactory.giveawayCompleted(
                            event.confirmerNickname(),
                            event.target().name()
                    );

            case ITEM_RENTAL ->
                    NotificationMessageFactory.itemRentalCompleted(
                            event.confirmerNickname(),
                            event.target().name()
                    );

            case COLLECTION_RENTAL ->
                    NotificationMessageFactory.collectionRentalCompleted(
                            event.confirmerNickname(),
                            event.target().name()
                    );

            case EXCHANGE ->
                    NotificationMessageFactory.exchangeCompleted(
                            event.confirmerNickname(),
                            event.target().name()
                    );

            case BEG ->
                    NotificationMessageFactory.begCompleted(
                            event.confirmerNickname(),
                            event.target().name()
                    );
        };
    }
}