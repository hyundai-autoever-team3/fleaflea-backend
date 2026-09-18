package com.anabada.fleaflea.domain.trade.service;

import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import com.anabada.fleaflea.domain.collection.repository.CollectionItemRepository;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeRequest;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeType;
import com.anabada.fleaflea.domain.trade.domain.Trade;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import com.anabada.fleaflea.domain.trade.dto.CollectionTradeRequestCreateRequest;
import com.anabada.fleaflea.domain.trade.dto.CollectionTradeRequestResponse;
import com.anabada.fleaflea.domain.trade.event.*;
import com.anabada.fleaflea.domain.trade.repository.CollectionTradeRequestRepository;
import com.anabada.fleaflea.domain.trade.repository.TradeRepository;
import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CollectionTradeService {
    private static final List<TradeRequestStatus> ACTIVE =
            List.of(TradeRequestStatus.PENDING, TradeRequestStatus.ACCEPTED);

    private final CollectionTradeRequestRepository requests;
    private final CollectionItemRepository items;
    private final MemberRepository members;
    private final FriendshipRepository friendships;
    private final TradeRepository trades;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CollectionTradeRequestResponse create(Long requesterId, Long itemId,
                                                  CollectionTradeRequestCreateRequest body) {
        Member requester = members.findById(requesterId)
                .orElseThrow(() -> error(ErrorCode.MEMBER_NOT_FOUND));
        CollectionItem target = items.findById(itemId)
                .orElseThrow(() -> error(ErrorCode.COLLECTION_ITEM_NOT_FOUND));
        Long ownerId = target.getOwner().getMemberId();
        if (ownerId.equals(requesterId)) throw error(ErrorCode.COLLECTION_TRADE_SELF_REQUEST);
        if (!Boolean.TRUE.equals(target.getIsPublic()) || !areFriends(ownerId, requesterId))
            throw error(ErrorCode.COLLECTION_TRADE_ACCESS_DENIED);
        if (requests.existsByRequesterAndCollectionItemAndStatusIn(requester, target, ACTIVE))
            throw error(ErrorCode.COLLECTION_TRADE_DUPLICATE_REQUEST);

        CollectionItem offer = null;
        if (body.tradeType() == CollectionTradeType.EXCHANGE) {
            if (body.offerCollectionItemId() == null)
                throw error(ErrorCode.COLLECTION_TRADE_OFFER_REQUIRED);
            offer = items.findById(body.offerCollectionItemId())
                    .orElseThrow(() -> error(ErrorCode.COLLECTION_TRADE_INVALID_OFFER));
            if (!offer.isOwnedBy(requesterId) || offer.getCollectionItemId().equals(itemId))
                throw error(ErrorCode.COLLECTION_TRADE_INVALID_OFFER);
        } else if (body.offerCollectionItemId() != null) {
            throw error(ErrorCode.COLLECTION_TRADE_INVALID_OFFER);
        }

        CollectionTradeRequest tradeRequest =
                CollectionTradeRequest.create(
                        target,
                        requester,
                        offer,
                        body.tradeType()
                );

        requests.save(tradeRequest);

        eventPublisher.publishEvent(
                TradeRequestedEvent.of(
                        tradeRequest.getCollectionTradeRequestId(),
                        requester.getMemberId(),
                        ownerId,
                        requester.getNickname(),
                        toTradeTarget(target)
                )
        );

        return CollectionTradeRequestResponse.from(tradeRequest);
    }
    @Transactional(readOnly = true)
    public CollectionTradeRequestResponse detail(Long memberId, Long id) {
        CollectionTradeRequest request = requests.findWithDetailsByCollectionTradeRequestId(id)
                .orElseThrow(() -> error(ErrorCode.COLLECTION_TRADE_REQUEST_NOT_FOUND));
        requireParty(request, memberId);
        return CollectionTradeRequestResponse.from(request);
    }

    @Transactional
    public CollectionTradeRequestResponse accept(Long memberId, Long id) {
        CollectionTradeRequest request = locked(id);
        requireOwner(request, memberId);
        requireStatus(request, TradeRequestStatus.PENDING);
        request.accept();

        Member owner = request.getCollectionItem().getOwner();

        eventPublisher.publishEvent(
                TradeAcceptedEvent.of(
                        request.getCollectionTradeRequestId(),
                        request.getRequester().getMemberId(),
                        owner.getMemberId(),
                        owner.getNickname(),
                        toTradeTarget(
                                request.getCollectionItem()
                        )
                )
        );

        return CollectionTradeRequestResponse.from(request);
    }

    @Transactional
    public CollectionTradeRequestResponse reject(Long memberId, Long id) {
        CollectionTradeRequest request = locked(id);
        requireOwner(request, memberId);
        requireStatus(request, TradeRequestStatus.PENDING);
        request.reject();

        Member owner =
                request.getCollectionItem().getOwner();

        eventPublisher.publishEvent(
                TradeRejectedEvent.of(
                        request.getCollectionTradeRequestId(),
                        request.getRequester().getMemberId(),
                        owner.getMemberId(),
                        owner.getNickname(),
                        toTradeTarget(
                                request.getCollectionItem()
                        )
                )
        );

        return CollectionTradeRequestResponse.from(request);
    }

    @Transactional
    public CollectionTradeRequestResponse cancel(Long memberId, Long id) {
        CollectionTradeRequest request = locked(id);
        if (!request.getRequester().getMemberId().equals(memberId))
            throw error(ErrorCode.COLLECTION_TRADE_ACCESS_DENIED);
        requireStatus(request, TradeRequestStatus.PENDING);
        request.cancel();

        eventPublisher.publishEvent(
                TradeCancelledEvent.of(
                        request.getCollectionTradeRequestId(),
                        request.getRequester().getMemberId(),
                        request.getCollectionItem()
                                .getOwner()
                                .getMemberId(),
                        request.getRequester().getNickname(),
                        toTradeTarget(
                                request.getCollectionItem()
                        )
                )
        );

        return CollectionTradeRequestResponse.from(request);
    }

    @Transactional
    public CollectionTradeRequestResponse complete(Long memberId, Long id) {
        CollectionTradeRequest request = locked(id);
        requireOwner(request, memberId);
        requireStatus(request, TradeRequestStatus.ACCEPTED);
        if (trades.existsByCollectionTradeRequestId(id))
            throw error(ErrorCode.COLLECTION_TRADE_INVALID_STATUS);
        request.complete();

        Member owner =
                request.getCollectionItem().getOwner();

        Member requester =
                request.getRequester();

        trades.save(
                Trade.ofCollectionTrade(
                        id,
                        requester.getMemberId(),
                        owner.getMemberId()
                )
        );

        eventPublisher.publishEvent(
                TradeCompletedEvent.of(
                        request.getCollectionTradeRequestId(),
                        owner.getMemberId(),
                        requester.getMemberId(),
                        owner.getNickname(),
                        toTradeTarget(
                                request.getCollectionItem()
                        )
                )
        );

        return CollectionTradeRequestResponse.from(request);
    }

    private CollectionTradeRequest locked(Long id) {
        return requests.findLockedByCollectionTradeRequestId(id)
                .orElseThrow(() -> error(ErrorCode.COLLECTION_TRADE_REQUEST_NOT_FOUND));
    }

    private void requireOwner(CollectionTradeRequest request, Long memberId) {
        if (!request.getCollectionItem().getOwner().getMemberId().equals(memberId))
            throw error(ErrorCode.COLLECTION_TRADE_ACCESS_DENIED);
    }

    private void requireParty(CollectionTradeRequest request, Long memberId) {
        if (!request.getRequester().getMemberId().equals(memberId)
                && !request.getCollectionItem().getOwner().getMemberId().equals(memberId))
            throw error(ErrorCode.COLLECTION_TRADE_ACCESS_DENIED);
    }

    private void requireStatus(CollectionTradeRequest request, TradeRequestStatus expected) {
        if (request.getStatus() != expected) throw error(ErrorCode.COLLECTION_TRADE_INVALID_STATUS);
    }

    private boolean areFriends(Long first, Long second) {
        return friendships.existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(
                first, second, FriendshipStatus.ACCEPTED)
                || friendships.existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(
                second, first, FriendshipStatus.ACCEPTED);
    }

    private BusinessException error(ErrorCode code) {
        return new BusinessException(code);
    }

    private TradeTarget toTradeTarget(
            CollectionItem collectionItem
    ) {
        return TradeTarget.of(
                TradeKind.COLLECTION,
                collectionItem.getCollectionItemId(),
                collectionItem.getTitle()
        );
    }

}
