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
            Long requesterId, Long collectionItemId, CollectionTradeRequestCreateRequest request
    ) {
        Member requester = memberRepository.findById(requesterId)
                .orElseThrow(MemberNotFoundException::new);
        List<Long> itemIds = new ArrayList<>();
        itemIds.add(collectionItemId);
        if (request.tradeType() == CollectionTradeType.EXCHANGE) {
            if (request.offerCollectionItemId() == null) {
                throw new CollectionTradeOfferRequiredException();
            }
            itemIds.add(request.offerCollectionItemId());
        } else if (request.offerCollectionItemId() != null) {
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
        if (request.tradeType() == CollectionTradeType.EXCHANGE) {
            offer = lockedItems.get(request.offerCollectionItemId());
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
                        request.tradeType()
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
    public CollectionTradeRequestResponse getCollectionTradeRequest(Long memberId, Long collectionTradeRequestId) {
        CollectionTradeRequest request = collectionTradeRequestRepository.findWithDetailsByCollectionTradeRequestId(collectionTradeRequestId)
                .orElseThrow(CollectionTradeRequestNotFoundException::new);
        requireParty(request, memberId);
        return CollectionTradeRequestResponse.from(request);
    }

    @Transactional
    public CollectionTradeRequestResponse acceptCollectionTradeRequest(Long memberId, Long collectionTradeRequestId) {
        CollectionTradeRequest request = findLockedCollectionTradeRequest(collectionTradeRequestId);
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
    public CollectionTradeRequestResponse rejectCollectionTradeRequest(Long memberId, Long collectionTradeRequestId) {
        CollectionTradeRequest request = findLockedCollectionTradeRequest(collectionTradeRequestId);
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
    public CollectionTradeRequestResponse cancelCollectionTradeRequest(Long memberId, Long collectionTradeRequestId) {
        CollectionTradeRequest request = findLockedCollectionTradeRequest(collectionTradeRequestId);
        if (!request.getRequester().getMemberId().equals(memberId)) {
            throw new CollectionTradeAccessDeniedException();
        }
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
    public CollectionTradeRequestResponse completeCollectionTradeRequest(Long memberId, Long collectionTradeRequestId) {
        CollectionTradeRequest request = findLockedCollectionTradeRequest(collectionTradeRequestId);
        requireRequester(request, memberId);
        requireStatus(request, TradeRequestStatus.ACCEPTED);
        if (tradeRepository.existsByCollectionTradeRequestId(collectionTradeRequestId)) {
            throw new CollectionTradeInvalidStatusException();
        }

        Member owner = request.getOwner();
        Member requester = request.getRequester();

        exchangeOwnership(request, owner, requester);
        request.complete();

        tradeRepository.save(
                Trade.ofCollectionTrade(
                        collectionTradeRequestId,
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

    private CollectionTradeRequest findLockedCollectionTradeRequest(Long collectionTradeRequestId) {
        return collectionTradeRequestRepository.findLockedByCollectionTradeRequestId(collectionTradeRequestId)
                .orElseThrow(CollectionTradeRequestNotFoundException::new);
    }

    private void requireOwner(CollectionTradeRequest request, Long memberId) {
        if (!request.getOwner().getMemberId().equals(memberId)) {
            throw new CollectionTradeAccessDeniedException();
        }
    }

    private void requireRequester(CollectionTradeRequest request, Long memberId) {
        if (!request.getRequester().getMemberId().equals(memberId)) {
            throw new CollectionTradeAccessDeniedException();
        }
    }

    private void requireParty(CollectionTradeRequest request, Long memberId) {
        if (!request.getRequester().getMemberId().equals(memberId)
                && !request.getOwner().getMemberId().equals(memberId)) {
            throw new CollectionTradeAccessDeniedException();
        }
    }

    private void exchangeOwnership(
            CollectionTradeRequest request,
            Member owner,
            Member requester
    ) {
        if (request.getTradeType() != CollectionTradeType.EXCHANGE) {
            return;
        }

        CollectionItem requestedItem = request.getCollectionItem();
        CollectionItem offeredItem = request.getOfferCollectionItem();
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

    private void requireStatus(CollectionTradeRequest request, TradeRequestStatus expected) {
        if (request.getStatus() != expected) {
            throw new CollectionTradeInvalidStatusException();
        }
    }

    private boolean areFriends(Long firstMemberId, Long secondMemberId) {
        return friendshipRepository.existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(
                firstMemberId, secondMemberId, FriendshipStatus.ACCEPTED)
                || friendshipRepository.existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(
                secondMemberId, firstMemberId, FriendshipStatus.ACCEPTED);
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
