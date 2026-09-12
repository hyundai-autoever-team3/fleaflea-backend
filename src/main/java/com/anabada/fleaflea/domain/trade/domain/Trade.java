package com.anabada.fleaflea.domain.trade.domain;

import com.anabada.fleaflea.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
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

    @Column(nullable = false)
    private Long collectionTradeRequestId;

    @Column(nullable = false)
    private Long tradeRequestId;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Long buyerId;

    @Column(nullable = false)
    private Long sellerId;

    private LocalDateTime completedAt;
}