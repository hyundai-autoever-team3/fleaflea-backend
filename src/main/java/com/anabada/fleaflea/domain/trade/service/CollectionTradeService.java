package com.anabada.fleaflea.domain.trade.service;

import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import com.anabada.fleaflea.domain.collection.exception.CollectionItemNotFoundException;
import com.anabada.fleaflea.domain.collection.repository.CollectionItemRepository;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeRequest;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeType;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestDirection;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import com.anabada.fleaflea.domain.trade.dto.CollectionTradeCreateRequest;
import com.anabada.fleaflea.domain.trade.dto.CollectionTradeResponse;
import com.anabada.fleaflea.domain.trade.exception.CollectionTradeException;
import com.anabada.fleaflea.domain.trade.repository.CollectionTradeRequestRepository;
import com.anabada.fleaflea.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import com.anabada.fleaflea.domain.member.dto.MemberSummaryResponse;
import com.anabada.fleaflea.global.image.ImageService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionTradeService {

    private static final List<TradeRequestStatus> ACTIVE_STATUSES =
            List.of(
                    TradeRequestStatus.PENDING,
                    TradeRequestStatus.ACCEPTED
            );

    private final CollectionTradeRequestRepository tradeRequestRepository;
    private final CollectionItemRepository collectionItemRepository;
    private final MemberRepository memberRepository;
    private final FriendshipRepository friendshipRepository;
    private final ImageService imageService;

    @Transactional
    public CollectionTradeResponse createTradeRequest(
            Long memberId,
            Long collectionItemId,
            CollectionTradeCreateRequest request
    ) {
        Member requester = findMember(memberId);
        CollectionItem targetItem = findCollectionItem(collectionItemId);

        if (targetItem.isOwnedBy(memberId)) {
            throw exception(ErrorCode.COLLECTION_TRADE_SELF_REQUEST);
        }

        if (!Boolean.TRUE.equals(targetItem.getIsPublic())) {
            throw exception(ErrorCode.COLLECTION_TRADE_ACCESS_DENIED);
        }

        Long ownerId = targetItem.getOwner().getMemberId();

        if (!isFriend(memberId, ownerId)) {
            throw exception(ErrorCode.COLLECTION_TRADE_ACCESS_DENIED);
        }

        boolean duplicate =
                tradeRequestRepository
                        .existsByTargetItem_CollectionItemIdAndRequester_MemberIdAndStatusIn(
                                collectionItemId,
                                memberId,
                                ACTIVE_STATUSES
                        );

        if (duplicate) {
            throw exception(ErrorCode.COLLECTION_TRADE_DUPLICATE_REQUEST);
        }

        CollectionItem offerItem =
                getOfferItem(memberId, targetItem, request);

        CollectionTradeRequest tradeRequest =
                CollectionTradeRequest.create(
                        targetItem,
                        requester,
                        offerItem,
                        request.tradeType()
                );

        return toResponse(
                tradeRequestRepository.save(tradeRequest)
        );
    }

    public CollectionTradeResponse getTradeRequest(
            Long memberId,
            Long requestId
    ) {
        CollectionTradeRequest tradeRequest =
                findTradeRequest(requestId);

        validateParty(tradeRequest, memberId);

        return toResponse(tradeRequest);
    }

    @Transactional
    public CollectionTradeResponse acceptTradeRequest(
            Long memberId,
            Long requestId
    ) {
        CollectionTradeRequest tradeRequest =
                findTradeRequest(requestId);

        validateOwner(tradeRequest, memberId);
        validatePending(tradeRequest);

        tradeRequest.accept();

        return toResponse(tradeRequest);
    }

    @Transactional
    public CollectionTradeResponse rejectTradeRequest(
            Long memberId,
            Long requestId
    ) {
        CollectionTradeRequest tradeRequest =
                findTradeRequest(requestId);

        validateOwner(tradeRequest, memberId);
        validatePending(tradeRequest);

        tradeRequest.reject();

        tradeRequestRepository.save(tradeRequest);

        return toResponse(tradeRequest);
    }

    @Transactional
    public CollectionTradeResponse cancelTradeRequest(
            Long memberId,
            Long requestId
    ) {
        CollectionTradeRequest tradeRequest =
                findTradeRequest(requestId);

        if (!tradeRequest.isRequestedBy(memberId)) {
            throw exception(ErrorCode.COLLECTION_TRADE_ACCESS_DENIED);
        }

        validatePending(tradeRequest);
        tradeRequest.cancel();

        tradeRequestRepository.save(tradeRequest);

        return toResponse(tradeRequest);
    }

    @Transactional
    public CollectionTradeResponse confirmCompletion(
            Long memberId,
            Long requestId
    ) {
        CollectionTradeRequest tradeRequest =
                findTradeRequest(requestId);

        validateParty(tradeRequest, memberId);

        if (!tradeRequest.isAccepted()) {
            throw exception(ErrorCode.COLLECTION_TRADE_INVALID_STATUS);
        }

        if (tradeRequest.isRequestedBy(memberId)) {
            if (tradeRequest.isRequesterConfirmed()) {
                throw exception(
                        ErrorCode.COLLECTION_TRADE_ALREADY_CONFIRMED
                );
            }

            tradeRequest.confirmByRequester();
        } else {
            if (tradeRequest.isOwnerConfirmed()) {
                throw exception(
                        ErrorCode.COLLECTION_TRADE_ALREADY_CONFIRMED
                );
            }

            tradeRequest.confirmByOwner();
        }
        tradeRequestRepository.save(tradeRequest);

        return toResponse(tradeRequest);
    }

    private CollectionItem getOfferItem(
            Long memberId,
            CollectionItem targetItem,
            CollectionTradeCreateRequest request
    ) {
        if (request.tradeType() == CollectionTradeType.RENTAL) {
            if (request.offerCollectionItemId() != null) {
                throw exception(ErrorCode.COLLECTION_TRADE_INVALID_OFFER);
            }

            return null;
        }

        if (request.offerCollectionItemId() == null) {
            throw exception(ErrorCode.COLLECTION_TRADE_OFFER_REQUIRED);
        }

        CollectionItem offerItem =
                findCollectionItem(request.offerCollectionItemId());

        if (!offerItem.isOwnedBy(memberId)) {
            throw exception(ErrorCode.COLLECTION_TRADE_ACCESS_DENIED);
        }

        if (offerItem.getCollectionItemId()
                .equals(targetItem.getCollectionItemId())) {
            throw exception(ErrorCode.COLLECTION_TRADE_INVALID_OFFER);
        }

        return offerItem;
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);
    }

    private CollectionItem findCollectionItem(Long collectionItemId) {
        return collectionItemRepository.findById(collectionItemId)
                .orElseThrow(CollectionItemNotFoundException::new);
    }

    private CollectionTradeRequest findTradeRequest(Long requestId) {
        return tradeRequestRepository.findById(requestId)
                .orElseThrow(() -> exception(
                        ErrorCode.COLLECTION_TRADE_REQUEST_NOT_FOUND
                ));
    }

    private void validateParty(
            CollectionTradeRequest tradeRequest,
            Long memberId
    ) {
        if (!tradeRequest.isParty(memberId)) {
            throw exception(ErrorCode.COLLECTION_TRADE_ACCESS_DENIED);
        }
    }

    private void validateOwner(
            CollectionTradeRequest tradeRequest,
            Long memberId
    ) {
        if (!tradeRequest.isOwnedBy(memberId)) {
            throw exception(ErrorCode.COLLECTION_TRADE_ACCESS_DENIED);
        }
    }

    private void validatePending(
            CollectionTradeRequest tradeRequest
    ) {
        if (!tradeRequest.isPending()) {
            throw exception(ErrorCode.COLLECTION_TRADE_INVALID_STATUS);
        }
    }

    private boolean isFriend(
            Long firstMemberId,
            Long secondMemberId
    ) {
        return friendshipRepository
                .existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(
                        firstMemberId,
                        secondMemberId,
                        FriendshipStatus.ACCEPTED
                )
                || friendshipRepository
                .existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(
                        secondMemberId,
                        firstMemberId,
                        FriendshipStatus.ACCEPTED
                );
    }

    private CollectionTradeException exception(
            ErrorCode errorCode
    ) {
        return new CollectionTradeException(errorCode);
    }

    private CollectionTradeResponse toResponse(
            CollectionTradeRequest tradeRequest
    ) {
        Member owner = tradeRequest.getTargetItem().getOwner();
        Member requester = tradeRequest.getRequester();

        MemberSummaryResponse ownerResponse =
                MemberSummaryResponse.from(
                        owner,
                        imageService.getUrl(owner.getProfileImageKey())
                );

        MemberSummaryResponse requesterResponse =
                MemberSummaryResponse.from(
                        requester,
                        imageService.getUrl(requester.getProfileImageKey())
                );

        return CollectionTradeResponse.from(
                tradeRequest,
                ownerResponse,
                requesterResponse
        );
    }

    // 거래 요청 목록 조회 (보낸 요청 / 받은 요청)
    public List<CollectionTradeResponse> getTradeRequests(
            Long memberId,
            TradeRequestDirection direction
    ) {
        List<CollectionTradeRequest> requests = switch (direction) {
            case RECEIVED -> tradeRequestRepository
                    .findByTargetItem_Owner_MemberIdOrderByCreatedAtDesc(memberId);
            case SENT -> tradeRequestRepository
                    .findByRequester_MemberIdOrderByCreatedAtDesc(memberId);
        };

        return requests.stream()
                .map(this::toResponse)
                .toList();
    }
}