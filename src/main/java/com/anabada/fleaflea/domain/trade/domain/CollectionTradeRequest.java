package com.anabada.fleaflea.domain.trade.domain;

import com.anabada.fleaflea.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Getter
@Entity
@Table(name = "collection_trade_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionTradeRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "collection_trade_request_id")
    private Long collectionTradeRequestId;

    @Column(name = "collection_item_id", nullable = false)
    private Long collectionItemId;
    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "offer_collection_item_id")
    private Long offerCollectionItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type", length = 20)
    private CollectionTradeType tradeType;
}
