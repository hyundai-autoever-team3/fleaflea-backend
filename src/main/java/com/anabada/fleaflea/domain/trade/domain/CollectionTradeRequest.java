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

    /**
     * 거래를 요청받은 대상 도감 아이템
     *
     * 기존 collectionItemId 대신 CollectionItem 연관관계를 사용
     * 실제 DB에는 collection_item_id 값이 저장
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_item_id", nullable = false)
    private CollectionItem targetItem;

    /**
     * 거래를 요청한 회원
     *
     * 기존 requesterId 대신 Member 연관관계를 사용
     * 실제 DB에는 requester_id 값이 저장
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private Member requester;

    /**
     * 교환을 제안할 때 요청자가 제공하는 도감 아이템
     *
     * RENTAL 요청에서는 null이 될 수 있음!!
     * EXCHANGE 요청에서는 서비스에서 필수 여부를 검증
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_collection_item_id")
    private CollectionItem offerItem;

    /**
     * 거래 방식: RENTAL 또는 EXCHANGE
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type", nullable = false, length = 20)
    private CollectionTradeType tradeType;

    /**
     * 거래 요청 진행 상태
     *
     * PENDING → ACCEPTED → COMPLETED
     * PENDING → REJECTED
     * PENDING → CANCELLED
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TradeRequestStatus status;

    /**
     * 요청자의 거래 완료 확인 여부
     */
    @Column(name = "requester_confirmed", nullable = false)
    private boolean requesterConfirmed;

    /**
     * 대상 아이템 소유자의 거래 완료 확인 여부
     */
    @Column(name = "owner_confirmed", nullable = false)
    private boolean ownerConfirmed;

    @Builder
    private CollectionTradeRequest(
            CollectionItem targetItem,
            Member requester,
            CollectionItem offerItem,
            CollectionTradeType tradeType
    ) {
        this.targetItem = targetItem;
        this.requester = requester;
        this.offerItem = offerItem;
        this.tradeType = tradeType;
        this.status = TradeRequestStatus.PENDING;
        this.requesterConfirmed = false;
        this.ownerConfirmed = false;
    }

    public static CollectionTradeRequest create(
            CollectionItem targetItem,
            Member requester,
            CollectionItem offerItem,
            CollectionTradeType tradeType
    ) {
        return CollectionTradeRequest.builder()
                .targetItem(targetItem)
                .requester(requester)
                .offerItem(offerItem)
                .tradeType(tradeType)
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

    public void confirmByRequester() {
        this.requesterConfirmed = true;
        completeIfBothConfirmed();
    }

    public void confirmByOwner() {
        this.ownerConfirmed = true;
        completeIfBothConfirmed();
    }

    private void completeIfBothConfirmed() {
        if (requesterConfirmed && ownerConfirmed) {
            this.status = TradeRequestStatus.COMPLETED;
        }
    }

    public boolean isPending() {
        return status == TradeRequestStatus.PENDING;
    }

    public boolean isAccepted() {
        return status == TradeRequestStatus.ACCEPTED;
    }

    public boolean isRequestedBy(Long memberId) {
        return requester.getMemberId().equals(memberId);
    }

    public boolean isOwnedBy(Long memberId) {
        return targetItem.isOwnedBy(memberId);
    }

    public boolean isParty(Long memberId) {
        return isRequestedBy(memberId) || isOwnedBy(memberId);
    }
}