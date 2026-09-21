package com.anabada.fleaflea.domain.trade.domain;

import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.item.domain.ItemTradeType;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.trade.exception.InvalidRentalPeriodException;
import com.anabada.fleaflea.domain.trade.exception.RentalPeriodRequiredException;
import com.anabada.fleaflea.domain.trade.exception.TradeRequestAlreadyConfirmedException;
import com.anabada.fleaflea.domain.trade.exception.TradeRequestNotAcceptedException;
import com.anabada.fleaflea.domain.trade.exception.TradeRequestNotParticipantException;
import com.anabada.fleaflea.domain.trade.exception.TradeRequestNotPendingException;
import com.anabada.fleaflea.domain.trade.exception.TradeRequestNotRequesterException;
import com.anabada.fleaflea.domain.trade.exception.TradeRequestNotSellerException;
import com.anabada.fleaflea.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private Member requester;

    @Column(columnDefinition = "TEXT")
    private String message;

    private LocalDate rentalStartDate;

    private LocalDate rentalEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TradeRequestStatus status;

    @Column(nullable = false)
    private boolean requesterConfirmed;

    @Builder
    private TradeRequest(
            Item item,
            Member requester,
            String message,
            LocalDate rentalStartDate,
            LocalDate rentalEndDate
    ) {
        this.item = item;
        this.requester = requester;
        this.message = message;
        updateRentalPeriod(item, rentalStartDate, rentalEndDate);
        this.status = TradeRequestStatus.PENDING;
        this.requesterConfirmed = false;
    }

    public static TradeRequest create(
            Item item,
            Member requester,
            String message,
            LocalDate rentalStartDate,
            LocalDate rentalEndDate
    ) {
        return TradeRequest.builder()
                .item(item)
                .requester(requester)
                .message(message)
                .rentalStartDate(rentalStartDate)
                .rentalEndDate(rentalEndDate)
                .build();
    }

    public void accept(Long memberId) {
        validateSeller(memberId);
        validatePending();

        this.status = TradeRequestStatus.ACCEPTED;
        item.startTrade();
    }

    public void reject(Long memberId) {
        validateSeller(memberId);
        validatePending();

        this.status = TradeRequestStatus.REJECTED;
    }

    public void cancel(Long memberId) {
        validateRequester(memberId);
        validatePending();

        this.status = TradeRequestStatus.CANCELLED;
    }
    public void confirmCompletion(Long memberId) {
        validateRequester(memberId);

        if (status != TradeRequestStatus.ACCEPTED) {
            throw new TradeRequestNotAcceptedException();
        }

        if (requesterConfirmed) {
            throw new TradeRequestAlreadyConfirmedException();
        }

        requesterConfirmed = true;
        status = TradeRequestStatus.COMPLETED;

        if (item.getTradeType() == ItemTradeType.RENTAL) {
            item.completeRental();
        } else {
            item.completeTrade();
        }
    }

    public void validateParticipant(Long memberId) {
        if (!isRequester(memberId) && !isSeller(memberId)) {
            throw new TradeRequestNotParticipantException();
        }
    }

    private void validateRequester(Long memberId) {
        if (!isRequester(memberId)) {
            throw new TradeRequestNotRequesterException();
        }
    }

    private void validateSeller(Long memberId) {
        if (!isSeller(memberId)) {
            throw new TradeRequestNotSellerException();
        }
    }

    private void validatePending() {
        if (status != TradeRequestStatus.PENDING) {
            throw new TradeRequestNotPendingException();
        }
    }

    private boolean isRequester(Long memberId) {
        return requester.getMemberId().equals(memberId);
    }

    private boolean isSeller(Long memberId) {
        return item.getSeller().getMemberId().equals(memberId);
    }

    private void updateRentalPeriod(
            Item item,
            LocalDate rentalStartDate,
            LocalDate rentalEndDate
    ) {
        if (item.getTradeType() != ItemTradeType.RENTAL) {
            this.rentalStartDate = null;
            this.rentalEndDate = null;
            return;
        }

        if (rentalStartDate == null || rentalEndDate == null) {
            throw new RentalPeriodRequiredException();
        }

        if (rentalEndDate.isBefore(rentalStartDate)) {
            throw new InvalidRentalPeriodException();
        }

        this.rentalStartDate = rentalStartDate;
        this.rentalEndDate = rentalEndDate;
    }
}
