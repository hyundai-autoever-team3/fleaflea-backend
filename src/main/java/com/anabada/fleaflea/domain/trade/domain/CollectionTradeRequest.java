package com.anabada.fleaflea.domain.trade.domain;

import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "collection_trade_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionTradeRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "collection_trade_request_id")
    private Long collectionTradeRequestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_item_id", nullable = false)
    private CollectionItem collectionItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private Member requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_collection_item_id")
    private CollectionItem offerCollectionItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type", nullable = false, length = 20)
    private CollectionTradeType tradeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TradeRequestStatus status;

    @Builder
    private CollectionTradeRequest(
            CollectionItem collectionItem,
            Member requester,
            CollectionItem offerCollectionItem,
            CollectionTradeType tradeType,
            TradeRequestStatus status
    ) {
        this.collectionItem = collectionItem;
        this.requester = requester;
        this.offerCollectionItem = offerCollectionItem;
        this.tradeType = tradeType;
        this.status = status;
    }

    public static CollectionTradeRequest create(
            CollectionItem collectionItem,
            Member requester,
            CollectionItem offerCollectionItem,
            CollectionTradeType tradeType
    ) {
        return CollectionTradeRequest.builder()
                .collectionItem(collectionItem)
                .requester(requester)
                .offerCollectionItem(offerCollectionItem)
                .tradeType(tradeType)
                .status(TradeRequestStatus.PENDING)
                .build();
    }

    public void accept() {
        this.status = TradeRequestStatus.ACCEPTED;
    }

    public void reject() {
        this.status = TradeRequestStatus.REJECTED;
    }

    public void cancel() {
        this.status = TradeRequestStatus.CANCELLED;
    }

    public void complete() {
        this.status = TradeRequestStatus.COMPLETED;
    }
}
