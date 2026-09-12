package com.anabada.fleaflea.domain.collectiontraderequest.domain;

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
    private Long collectionTradeRequestId;

    @Column(nullable = false)
    private Long collectionItemId;

    @Column(nullable = false)
    private Long requesterId;

    private Long offerCollectionItemId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CollectionTradeType status;
}