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

    @Column
    private Long collectionTradeRequestId;

    @Column
    private Long tradeRequestId;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Long buyerId;

    @Column(nullable = false)
    private Long sellerId;

    private LocalDateTime completedAt;

    @Builder
    private Trade(
            Long tradeRequestId,
            Long itemId,
            Long buyerId,
            Long sellerId,
            LocalDateTime completedAt
    ) {
        this.tradeRequestId = tradeRequestId;
        this.itemId = itemId;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.completedAt = completedAt;
    }

    public static Trade create(TradeRequest tradeRequest) {
        return Trade.builder()
                .tradeRequestId(tradeRequest.getTradeRequestId())
                .itemId(tradeRequest.getItem().getItemId())
                .buyerId(tradeRequest.getRequester().getMemberId())
                .sellerId(tradeRequest.getItem().getSeller().getMemberId())
                .completedAt(LocalDateTime.now())
                .build();
    }
}