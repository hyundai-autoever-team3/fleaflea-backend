package com.anabada.fleaflea.domain.trade.service;

import com.anabada.fleaflea.domain.collectionitem.domain.CollectionItem;
import com.anabada.fleaflea.domain.collectionitem.exception.CollectionItemNotFoundException;
import com.anabada.fleaflea.domain.collectionitem.repository.CollectionItemRepository;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.notification.notifier.TradeNotifier;
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
import com.anabada.fleaflea.domain.trade.exception.CollectionTradeAccessDeniedException;
import com.anabada.fleaflea.domain.trade.exception.CollectionTradeDuplicateRequestException;
import com.anabada.fleaflea.domain.trade.exception.CollectionTradeInvalidOfferException;
import com.anabada.fleaflea.domain.trade.exception.CollectionTradeInvalidStatusException;
import com.anabada.fleaflea.domain.trade.exception.CollectionTradeOfferRequiredException;
import com.anabada.fleaflea.domain.trade.exception.CollectionTradeOwnershipChangedException;
import com.anabada.fleaflea.domain.trade.exception.CollectionTradeRequestNotFoundException;
import com.anabada.fleaflea.domain.trade.exception.CollectionTradeSelfRequestException;
import com.anabada.fleaflea.domain.trade.repository.CollectionTradeRequestRepository;
import com.anabada.fleaflea.domain.trade.repository.TradeRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CollectionTradeService {

    private static final List<TradeRequestStatus> ACTIVE_REQUEST_STATUSES =
            List.of(TradeRequestStatus.PENDING, TradeRequestStatus.ACCEPTED);

    private final CollectionTradeRequestRepository collectionTradeRequestRepository;
    private final CollectionItemRepository collectionItemRepository;
    private final MemberRepository memberRepository;
    private final FriendshipRepository friendshipRepository;
    private final TradeRepository tradeRepository;
    private final TradeNotifier tradeNotifier;

    @Transactional
    public CollectionTradeRequestResponse createCollectionTradeRequest(
            Long requesterId,
            Long collectionItemId,
            CollectionTradeRequestCreateRequest collectionTradeRequestCreateRequest
    ) {
        Member requester = memberRepository.findById(requesterId)
                .orElseThrow(MemberNotFoundException::new);
        List<Long> itemIds = new ArrayList<>();
        itemIds.add(collectionItemId);
        if (collectionTradeRequestCreateRequest.tradeType() == CollectionTradeType.EXCHANGE) {
            if (collectionTradeRequestCreateRequest.offerCollectionItemId() == null) {
                throw new CollectionTradeOfferRequiredException();
            }
            itemIds.add(collectionTradeRequestCreateRequest.offerCollectionItemId());
        } else if (collectionTradeRequestCreateRequest.offerCollectionItemId() != null) {
            throw new CollectionTradeInvalidOfferException();
        }
        Map<Long, CollectionItem> lockedItems = collectionItemRepository.findAllByIdForUpdate(itemIds).stream()
                .collect(Collectors.toMap(CollectionItem::getCollectionItemId, Function.identity()));
        CollectionItem target = lockedItems.get(collectionItemId);
        if (target == null) {
            throw new CollectionItemNotFoundException();
        }
        Long ownerId = target.getOwner().getMemberId();
        if (ownerId.equals(requesterId)) {
            throw new CollectionTradeSelfRequestException();
        }
        if (!Boolean.TRUE.equals(target.getIsPublic()) || !areFriends(ownerId, requesterId)) {
            throw new CollectionTradeAccessDeniedException();
        }
        if (collectionTradeRequestRepository.existsByRequesterAndCollectionItemAndStatusIn(requester, target, ACTIVE_REQUEST_STATUSES)) {
            throw new CollectionTradeDuplicateRequestException();
        }

        CollectionItem offer = null;
        if (collectionTradeRequestCreateRequest.tradeType() == CollectionTradeType.EXCHANGE) {
            offer = lockedItems.get(collectionTradeRequestCreateRequest.offerCollectionItemId());
            if (offer == null || !offer.isOwnedBy(requesterId)
                    || offer.getCollectionItemId().equals(collectionItemId)) {
                throw new CollectionTradeInvalidOfferException();
            }
        }

        CollectionTradeRequest tradeRequest =
                CollectionTradeRequest.create(
                        target,
                        requester,
                        offer,
                        collectionTradeRequestCreateRequest.tradeType()
                );

        collectionTradeRequestRepository.save(tradeRequest);

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
    public CollectionTradeRequestResponse getCollectionTradeRequest(
            Long memberId,
            Long collectionTradeRequestId
    ) {
        CollectionTradeRequest collectionTradeRequest = collectionTradeRequestRepository
                .findWithDetailsByCollectionTradeRequestId(collectionTradeRequestId)
                .orElseThrow(CollectionTradeRequestNotFoundException::new);
        requireParty(collectionTradeRequest, memberId);
        return CollectionTradeRequestResponse.from(collectionTradeRequest);
    }

    @Transactional
    public CollectionTradeRequestResponse acceptCollectionTradeRequest(
            Long memberId,
            Long collectionTradeRequestId
    ) {
        CollectionTradeRequest collectionTradeRequest = findLockedCollectionTradeRequest(collectionTradeRequestId);
        requireOwner(collectionTradeRequest, memberId);
        requireStatus(collectionTradeRequest, TradeRequestStatus.PENDING);
        collectionTradeRequest.accept();

        Member owner = collectionTradeRequest.getOwner();

        tradeNotifier.notifyOf(
                TradeAcceptedEvent.of(
                        collectionTradeRequest.getCollectionTradeRequestId(),
                        collectionTradeRequest.getRequester().getMemberId(),
                        owner.getMemberId(),
                        owner.getNickname(),
                        toTradeTarget(collectionTradeRequest)
                )
        );

        return CollectionTradeRequestResponse.from(collectionTradeRequest);
    }

    @Transactional
    public CollectionTradeRequestResponse rejectCollectionTradeRequest(
            Long memberId,
            Long collectionTradeRequestId
    ) {
        CollectionTradeRequest collectionTradeRequest = findLockedCollectionTradeRequest(collectionTradeRequestId);
        requireOwner(collectionTradeRequest, memberId);
        requireStatus(collectionTradeRequest, TradeRequestStatus.PENDING);
        collectionTradeRequest.reject();

        Member owner = collectionTradeRequest.getOwner();

        tradeNotifier.notifyOf(
                TradeRejectedEvent.of(
                        collectionTradeRequest.getCollectionTradeRequestId(),
                        collectionTradeRequest.getRequester().getMemberId(),
                        owner.getMemberId(),
                        owner.getNickname(),
                        toTradeTarget(collectionTradeRequest)
                )
        );

        return CollectionTradeRequestResponse.from(collectionTradeRequest);
    }

    @Transactional
    public CollectionTradeRequestResponse cancelCollectionTradeRequest(
            Long memberId,
            Long collectionTradeRequestId
    ) {
        CollectionTradeRequest collectionTradeRequest = findLockedCollectionTradeRequest(collectionTradeRequestId);
        if (!collectionTradeRequest.getRequester().getMemberId().equals(memberId)) {
            throw new CollectionTradeAccessDeniedException();
        }
        requireStatus(collectionTradeRequest, TradeRequestStatus.PENDING);
        collectionTradeRequest.cancel();

        tradeNotifier.notifyOf(
                TradeCancelledEvent.of(
                        collectionTradeRequest.getCollectionTradeRequestId(),
                        collectionTradeRequest.getRequester().getMemberId(),
                        collectionTradeRequest.getOwner().getMemberId(),
                        collectionTradeRequest.getRequester()
                                .getNickname(),
                        toTradeTarget(collectionTradeRequest)
                )
        );

        return CollectionTradeRequestResponse.from(collectionTradeRequest);
    }

    @Transactional
    public CollectionTradeRequestResponse completeCollectionTradeRequest(
            Long memberId,
            Long collectionTradeRequestId
    ) {
        CollectionTradeRequest collectionTradeRequest = findLockedCollectionTradeRequest(collectionTradeRequestId);
        requireRequester(collectionTradeRequest, memberId);
        requireStatus(collectionTradeRequest, TradeRequestStatus.ACCEPTED);
        if (tradeRepository.existsByCollectionTradeRequestId(collectionTradeRequestId)) {
            throw new CollectionTradeInvalidStatusException();
        }

        Member owner = collectionTradeRequest.getOwner();
        Member requester = collectionTradeRequest.getRequester();

        exchangeOwnership(collectionTradeRequest, owner, requester);
        collectionTradeRequest.complete();

        tradeRepository.save(
                Trade.ofCollectionTrade(
                        collectionTradeRequestId,
                        requester.getMemberId(),
                        owner.getMemberId()
                )
        );

        tradeNotifier.notifyOf(
                TradeCompletedEvent.of(
                        collectionTradeRequest.getCollectionTradeRequestId(),
                        requester.getMemberId(),
                        owner.getMemberId(),
                        requester.getNickname(),
                        toTradeTarget(collectionTradeRequest)
                )
        );

        return CollectionTradeRequestResponse.from(collectionTradeRequest);
    }

    private CollectionTradeRequest findLockedCollectionTradeRequest(Long collectionTradeRequestId) {
        return collectionTradeRequestRepository.findLockedByCollectionTradeRequestId(collectionTradeRequestId)
                .orElseThrow(CollectionTradeRequestNotFoundException::new);
    }

    private void requireOwner(
            CollectionTradeRequest collectionTradeRequest,
            Long memberId
    ) {
        if (!collectionTradeRequest.getOwner().getMemberId().equals(memberId)) {
            throw new CollectionTradeAccessDeniedException();
        }
    }

    private void requireRequester(
            CollectionTradeRequest collectionTradeRequest,
            Long memberId
    ) {
        if (!collectionTradeRequest.getRequester().getMemberId().equals(memberId)) {
            throw new CollectionTradeAccessDeniedException();
        }
    }

    private void requireParty(
            CollectionTradeRequest collectionTradeRequest,
            Long memberId
    ) {
        if (!collectionTradeRequest.getRequester().getMemberId().equals(memberId)
                && !collectionTradeRequest.getOwner().getMemberId().equals(memberId)) {
            throw new CollectionTradeAccessDeniedException();
        }
    }

    private void exchangeOwnership(
            CollectionTradeRequest collectionTradeRequest,
            Member owner,
            Member requester
    ) {
        if (collectionTradeRequest.getTradeType() != CollectionTradeType.EXCHANGE) {
            return;
        }

        CollectionItem requestedItem = collectionTradeRequest.getCollectionItem();
        CollectionItem offeredItem = collectionTradeRequest.getOfferCollectionItem();
        if (offeredItem == null) {
            throw new CollectionTradeOwnershipChangedException();
        }

        Map<Long, CollectionItem> lockedItems = collectionItemRepository.findAllByIdForUpdate(List.of(
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
            throw new CollectionTradeOwnershipChangedException();
        }

        lockedRequestedItem.transferTo(requester);
        lockedOfferedItem.transferTo(owner);
    }

    private void requireStatus(
            CollectionTradeRequest collectionTradeRequest,
            TradeRequestStatus expected
    ) {
        if (collectionTradeRequest.getStatus() != expected) {
            throw new CollectionTradeInvalidStatusException();
        }
    }

    private boolean areFriends(
            Long firstMemberId,
            Long secondMemberId
    ) {
        return friendshipRepository.existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(
                firstMemberId, secondMemberId, FriendshipStatus.ACCEPTED)
                || friendshipRepository.existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(
                secondMemberId, firstMemberId, FriendshipStatus.ACCEPTED);
    }

    private TradeTarget toTradeTarget(
            CollectionTradeRequest collectionTradeRequest
    ) {
        CollectionItem collectionItem =
                collectionTradeRequest.getCollectionItem();

        return TradeTarget.of(
                TradeKind.COLLECTION,
                toTradeDealType(collectionTradeRequest),
                collectionItem.getCollectionItemId(),
                collectionItem.getTitle()
        );
    }

    private TradeDealType toTradeDealType(
            CollectionTradeRequest collectionTradeRequest
    ) {
        return switch (collectionTradeRequest.getTradeType()) {
            case RENTAL ->
                    TradeDealType.COLLECTION_RENTAL;

            case EXCHANGE ->
                    TradeDealType.EXCHANGE;
        };
    }
}
