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
import com.anabada.fleaflea.domain.trade.event.TradeAcceptedEvent;
import com.anabada.fleaflea.domain.trade.event.TradeCancelledEvent;
import com.anabada.fleaflea.domain.trade.event.TradeCompletedEvent;
import com.anabada.fleaflea.domain.trade.event.TradeDealType;
import com.anabada.fleaflea.domain.trade.event.TradeKind;
import com.anabada.fleaflea.domain.trade.event.TradeRejectedEvent;
import com.anabada.fleaflea.domain.trade.event.TradeRequestedEvent;
import com.anabada.fleaflea.domain.trade.event.TradeTarget;
import com.anabada.fleaflea.domain.trade.repository.CollectionTradeRequestRepository;
import com.anabada.fleaflea.domain.trade.repository.TradeRepository;
import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import com.anabada.fleaflea.domain.notification.notifier.TradeNotifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final TradeNotifier tradeNotifier;

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

        tradeNotifier.notifyOf(
                TradeRequestedEvent.of(
                        tradeRequest.getCollectionTradeRequestId(),
                        requester.getMemberId(),
                        ownerId,
                        requester.getNickname(),
                        toTradeTarget(tradeRequest)
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

        Member owner = request.getOwner();

        tradeNotifier.notifyOf(
                TradeAcceptedEvent.of(
                        request.getCollectionTradeRequestId(),
                        request.getRequester().getMemberId(),
                        owner.getMemberId(),
                        owner.getNickname(),
                        toTradeTarget(request)
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

        Member owner = request.getOwner();

        tradeNotifier.notifyOf(
                TradeRejectedEvent.of(
                        request.getCollectionTradeRequestId(),
                        request.getRequester().getMemberId(),
                        owner.getMemberId(),
                        owner.getNickname(),
                        toTradeTarget(request)
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

        tradeNotifier.notifyOf(
                TradeCancelledEvent.of(
                        request.getCollectionTradeRequestId(),
                        request.getRequester().getMemberId(),
                        request.getOwner().getMemberId(),
                        request.getRequester()
                                .getNickname(),
                        toTradeTarget(request)
                )
        );

        return CollectionTradeRequestResponse.from(request);
    }

    @Transactional
    public CollectionTradeRequestResponse complete(Long memberId, Long id) {
        CollectionTradeRequest request = locked(id);
        requireRequester(request, memberId);
        requireStatus(request, TradeRequestStatus.ACCEPTED);
        if (trades.existsByCollectionTradeRequestId(id))
            throw error(ErrorCode.COLLECTION_TRADE_INVALID_STATUS);

        Member owner = request.getOwner();
        Member requester = request.getRequester();

        exchangeOwnership(request, owner, requester);
        request.complete();

        trades.save(
                Trade.ofCollectionTrade(
                        id,
                        requester.getMemberId(),
                        owner.getMemberId()
                )
        );

        tradeNotifier.notifyOf(
                TradeCompletedEvent.of(
                        request.getCollectionTradeRequestId(),
                        requester.getMemberId(),
                        owner.getMemberId(),
                        requester.getNickname(),
                        toTradeTarget(request)
                )
        );

        return CollectionTradeRequestResponse.from(request);
    }

    private CollectionTradeRequest locked(Long id) {
        return requests.findLockedByCollectionTradeRequestId(id)
                .orElseThrow(() -> error(ErrorCode.COLLECTION_TRADE_REQUEST_NOT_FOUND));
    }

    private void requireOwner(CollectionTradeRequest request, Long memberId) {
        if (!request.getOwner().getMemberId().equals(memberId))
            throw error(ErrorCode.COLLECTION_TRADE_ACCESS_DENIED);
    }

    private void requireRequester(CollectionTradeRequest request, Long memberId) {
        if (!request.getRequester().getMemberId().equals(memberId))
            throw error(ErrorCode.COLLECTION_TRADE_ACCESS_DENIED);
    }

    private void requireParty(CollectionTradeRequest request, Long memberId) {
        if (!request.getRequester().getMemberId().equals(memberId)
                && !request.getOwner().getMemberId().equals(memberId))
            throw error(ErrorCode.COLLECTION_TRADE_ACCESS_DENIED);
    }

    private void exchangeOwnership(
            CollectionTradeRequest request,
            Member owner,
            Member requester
    ) {
        if (request.getTradeType() != CollectionTradeType.EXCHANGE) return;

        CollectionItem requestedItem = request.getCollectionItem();
        CollectionItem offeredItem = request.getOfferCollectionItem();
        if (offeredItem == null) throw error(ErrorCode.COLLECTION_TRADE_OWNERSHIP_CHANGED);

        Map<Long, CollectionItem> lockedItems = items.findAllByIdForUpdate(List.of(
                        requestedItem.getCollectionItemId(),
                        offeredItem.getCollectionItemId()
                )).stream()
                .collect(Collectors.toMap(CollectionItem::getCollectionItemId, Function.identity()));

        CollectionItem lockedRequestedItem = lockedItems.get(requestedItem.getCollectionItemId());
        CollectionItem lockedOfferedItem = lockedItems.get(offeredItem.getCollectionItemId());
        if (lockedRequestedItem == null
                || lockedOfferedItem == null
                || !lockedRequestedItem.isOwnedBy(owner.getMemberId())
                || !lockedOfferedItem.isOwnedBy(requester.getMemberId())) {
            throw error(ErrorCode.COLLECTION_TRADE_OWNERSHIP_CHANGED);
        }

        lockedRequestedItem.transferTo(requester);
        lockedOfferedItem.transferTo(owner);
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
            CollectionTradeRequest request
    ) {
        CollectionItem collectionItem =
                request.getCollectionItem();

        return TradeTarget.of(
                TradeKind.COLLECTION,
                toTradeDealType(request),
                collectionItem.getCollectionItemId(),
                collectionItem.getTitle()
        );
    }

    private TradeDealType toTradeDealType(
            CollectionTradeRequest request
    ) {
        return switch (request.getTradeType()) {
            case RENTAL ->
                    TradeDealType.COLLECTION_RENTAL;

            case EXCHANGE ->
                    TradeDealType.EXCHANGE;
        };
    }
}
