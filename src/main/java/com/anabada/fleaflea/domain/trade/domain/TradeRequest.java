package com.anabada.fleaflea.domain.trade.domain;

import com.anabada.fleaflea.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "trade_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tradeRequestId;

    @Column(nullable = false)
    private Long itemId;

    private Long swapItemId;

    @Column(nullable = false)
    private Long requesterId;

    private LocalDate rentalStartDate;

    private LocalDate rentalEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TradeRequestStatus status;
}