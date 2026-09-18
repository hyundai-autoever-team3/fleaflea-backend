package com.anabada.fleaflea.domain.trade.domain;

import com.anabada.fleaflea.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "trades")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trade extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tradeId;

    // 도감 거래 시 채워짐, 플리마켓 거래 시 null
    @Column(name = "collection_trade_request_id")
    private Long collectionTradeRequestId;

    // 구걸 거래 시 채워짐
    @Column(name = "beg_request_id")
    private Long begRequestId;

    // 플리마켓 거래 시 채워짐, 도감 거래 시 null
    @Column(name = "trade_request_id")
    private Long tradeRequestId;

    // 도감 거래에는 플리마켓 상품 ID가 없다.
    @Column(name = "item_id")
    private Long itemId;

    @Column(nullable = false)
    private Long buyerId;

    @Column(nullable = false)
    private Long sellerId;

    private LocalDateTime completedAt;

    @Builder
    private Trade(
            Long collectionTradeRequestId,
            Long begRequestId,
            Long tradeRequestId,
            Long itemId,
            Long buyerId,
            Long sellerId
    ) {
        this.collectionTradeRequestId = collectionTradeRequestId;
        this.begRequestId = begRequestId;
        this.tradeRequestId = tradeRequestId;
        this.itemId = itemId;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.completedAt = LocalDateTime.now();
    }

    public static Trade ofCollectionTrade(
            Long collectionTradeRequestId,
            Long buyerId,
            Long sellerId
    ) {
        return Trade.builder()
                .collectionTradeRequestId(collectionTradeRequestId)
                .buyerId(buyerId)
                .sellerId(sellerId)
                .build();
    }

    public static Trade ofBegRequest(
            Long begRequestId,
            Long buyerId,
            Long sellerId
    ) {
        return Trade.builder()
                .begRequestId(begRequestId)
                .buyerId(buyerId)
                .sellerId(sellerId)
                .build();
    }

    public static Trade create(TradeRequest tradeRequest) {
        return ofItemTrade(
                tradeRequest.getTradeRequestId(),
                tradeRequest.getItem().getItemId(),
                tradeRequest.getRequester().getMemberId(),
                tradeRequest.getItem().getSeller().getMemberId()
        );
    }

    public static Trade ofItemTrade(
            Long tradeRequestId,
            Long itemId,
            Long buyerId,
            Long sellerId
    ) {
        return Trade.builder()
                .tradeRequestId(tradeRequestId)
                .itemId(itemId)
                .buyerId(buyerId)
                .sellerId(sellerId)
                .build();
    }
}
