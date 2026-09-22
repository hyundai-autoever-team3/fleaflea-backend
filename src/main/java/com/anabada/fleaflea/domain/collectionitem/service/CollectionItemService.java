package com.anabada.fleaflea.domain.collectionitem.service;

import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemCreateRequest;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemResponse;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemSearchCondition;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemSummaryResponse;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemUpdateRequest;
import com.anabada.fleaflea.domain.collectionitem.exception.CollectionItemAccessDeniedException;
import com.anabada.fleaflea.domain.collectionitem.exception.CollectionItemNotFoundException;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.global.dto.PageResponse;
import com.anabada.fleaflea.global.image.ImageCategory;
import com.anabada.fleaflea.global.image.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.anabada.fleaflea.global.exception.BusinessException;
import com.anabada.fleaflea.global.exception.ErrorCode;
import java.util.List;

import java.util.List;

import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;

import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import com.anabada.fleaflea.domain.collection.domain.CollectionItemStatus;
import com.anabada.fleaflea.domain.collection.repository.CollectionItemRepository;
import com.anabada.fleaflea.domain.begrequest.domain.BegRequestStatus;
import com.anabada.fleaflea.domain.begrequest.repository.BegRequestRepository;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeType;
import com.anabada.fleaflea.domain.trade.repository.CollectionTradeRequestRepository;
import com.anabada.fleaflea.domain.trade.repository.TradeRequestRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionItemService {

    private static final List<BegRequestStatus> ACTIVE_BEG_STATUSES =
            List.of(BegRequestStatus.PENDING, BegRequestStatus.ACCEPTED);
    private static final List<TradeRequestStatus> ACTIVE_TRADE_STATUSES =
            List.of(TradeRequestStatus.PENDING, TradeRequestStatus.ACCEPTED);

    private final CollectionItemRepository collectionItemRepository;
    private final MemberRepository memberRepository;
    private final ImageService imageService;
    private final FriendshipRepository friendshipRepository;
    private final BegRequestRepository begRequestRepository;
    private final CollectionTradeRequestRepository collectionTradeRequestRepository;
    private final TradeRequestRepository tradeRequestRepository;

    @Transactional
    public CollectionItemResponse createCollectionItem(
            Long memberId,
            CollectionItemCreateRequest request
    ) {
        Member owner = getMember(memberId);

        String imageKey = null;

        if (request.image() != null
                && !request.image().isEmpty()) {
            imageKey = imageService.upload(
                    request.image(),
                    ImageCategory.COLLECTION_ITEM
            );
        }

        CollectionItem collectionItem = CollectionItem.create(
                owner,
                request.title(),
                request.description(),
                imageKey,
                request.isPublic()
        );

        CollectionItem savedItem =
                collectionItemRepository.save(collectionItem);

        return toResponse(savedItem);
    }

    public PageResponse<CollectionItemSummaryResponse> getMyCollectionItems(
            Long memberId,
            CollectionItemSearchCondition condition,
            Pageable pageable
    ) {
        Member owner = getMember(memberId);

        return PageResponse.from(
                collectionItemRepository
                        .search(
                                owner.getMemberId(),
                                false,
                                condition,
                                pageable
                        )
                        .map(this::toSummaryResponse)
        );
    }

    public PageResponse<CollectionItemSummaryResponse> getMemberCollectionItems(
            Long requesterId,
            Long ownerId,
            CollectionItemSearchCondition condition,
            Pageable pageable
    ) {
        Member requester = getMember(requesterId);
        Member owner = getMember(ownerId);

        validateCollectionViewer(
                requester.getMemberId(),
                owner.getMemberId()
        );

        return PageResponse.from(
                collectionItemRepository
                        .search(
                                owner.getMemberId(),
                                true,
                                condition,
                                pageable
                        )
                        .map(this::toSummaryResponse)
        );
    }

    public CollectionItemResponse getCollectionItem(
            Long memberId,
            Long collectionItemId
    ) {
        Member requester = getMember(memberId);

        CollectionItem collectionItem =
                getCollectionItem(collectionItemId);

        Long ownerId =
                collectionItem.getOwner().getMemberId();

        boolean owner = ownerId.equals(requester.getMemberId());

        if (owner) {
            return toResponse(collectionItem);
        }

        boolean publicItem =
                Boolean.TRUE.equals(collectionItem.getIsPublic());

        boolean friend = isFriend(
                requester.getMemberId(),
                ownerId
        );

        if (!publicItem || !friend) {
            throw new CollectionItemAccessDeniedException();
        }

        return toResponse(collectionItem);
    }

    @Transactional
    public CollectionItemResponse updateCollectionItem(
            Long memberId,
            Long collectionItemId,
            CollectionItemUpdateRequest request
    ) {
        CollectionItem collectionItem =
                getCollectionItem(collectionItemId);

        validateOwner(collectionItem, memberId);

        collectionItem.update(
                request.title(),
                request.description(),
                request.isPublic()
        );

        if (request.image() != null
                && !request.image().isEmpty()) {
            String previousImageKey =
                    collectionItem.getImageKey();

            String newImageKey;

            if (previousImageKey == null) {
                newImageKey = imageService.upload(
                        request.image(),
                        ImageCategory.COLLECTION_ITEM
                );
            } else {
                newImageKey = imageService.replace(
                        previousImageKey,
                        request.image(),
                        ImageCategory.COLLECTION_ITEM
                );
            }

            collectionItem.updateImageKey(newImageKey);
        }

        return toResponse(collectionItem);
    }

    @Transactional
    public void deleteCollectionItem(
            Long memberId,
            Long collectionItemId
    ) {
        CollectionItem collectionItem =
                getCollectionItem(collectionItemId);

        validateOwner(collectionItem, memberId);
        validateDeletable(collectionItemId);

        String imageKey = collectionItem.getImageKey();

        collectionTradeRequestRepository.deleteAllDeletableByCollectionItemId(
                collectionItemId,
                List.of(
                        TradeRequestStatus.PENDING,
                        TradeRequestStatus.REJECTED,
                        TradeRequestStatus.CANCELLED
                ),
                TradeRequestStatus.COMPLETED,
                CollectionTradeType.RENTAL
        );
        begRequestRepository.deleteAllByCollectionItemIdAndStatusIn(
                collectionItemId,
                List.of(
                        BegRequestStatus.PENDING,
                        BegRequestStatus.REJECTED,
                        BegRequestStatus.CANCELLED
                )
        );
        collectionItemRepository.delete(collectionItem);
        imageService.deleteAfterCommit(imageKey);
    }

    private void validateDeletable(Long collectionItemId) {
        boolean active = begRequestRepository
                .existsByCollectionItem_CollectionItemIdAndStatusIn(
                        collectionItemId, ACTIVE_BEG_STATUSES)
                || collectionTradeRequestRepository
                .existsByCollectionItem_CollectionItemIdAndStatusIn(
                        collectionItemId, ACTIVE_TRADE_STATUSES)
                || collectionTradeRequestRepository
                .existsByOfferCollectionItem_CollectionItemIdAndStatusIn(
                        collectionItemId, ACTIVE_TRADE_STATUSES)
                || tradeRequestRepository
                .existsByItem_CollectionItem_CollectionItemIdAndStatusIn(
                        collectionItemId, ACTIVE_TRADE_STATUSES);

        if (active) {
            throw new BusinessException(ErrorCode.COLLECTION_ITEM_TRADE_IN_PROGRESS);
        }
    }

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);
    }

    private CollectionItem getCollectionItem(
            Long collectionItemId
    ) {
        return collectionItemRepository
                .findById(collectionItemId)
                .orElseThrow(CollectionItemNotFoundException::new);
    }

    private void validateOwner(
            CollectionItem collectionItem,
            Long memberId
    ) {
        if (!collectionItem.isOwnedBy(memberId)) {
            throw new CollectionItemAccessDeniedException();
        }
    }

    private CollectionItemResponse toResponse(
            CollectionItem collectionItem
    ) {
        String imageUrl = imageService.getUrl(
                collectionItem.getImageKey()
        );

        return CollectionItemResponse.from(
                collectionItem,
                imageUrl,
                getStatus(collectionItem)
        );
    }

    private CollectionItemStatus getStatus(CollectionItem collectionItem) {
        Long collectionItemId = collectionItem.getCollectionItemId();

        boolean inProgress =
                begRequestRepository.existsByCollectionItem_CollectionItemIdAndStatus(
                        collectionItemId,
                        BegRequestStatus.ACCEPTED
                )
                || collectionTradeRequestRepository
                        .existsByCollectionItem_CollectionItemIdAndStatus(
                                collectionItemId,
                                TradeRequestStatus.ACCEPTED
                        )
                || tradeRequestRepository
                        .existsByItem_CollectionItem_CollectionItemIdAndStatus(
                                collectionItemId,
                                TradeRequestStatus.ACCEPTED
                        );

        return inProgress
                ? CollectionItemStatus.IN_PROGRESS
                : CollectionItemStatus.AVAILABLE;
    }

    private CollectionItemSummaryResponse toSummaryResponse(
            CollectionItem collectionItem
    ) {
        String imageUrl = imageService.getUrl(
                collectionItem.getImageKey()
        );

        return CollectionItemSummaryResponse.from(
                collectionItem,
                imageUrl
        );
    }

    private boolean isFriend(
            Long firstMemberId,
            Long secondMemberId
    ) {
        boolean firstToSecond =
                friendshipRepository
                        .existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(
                                firstMemberId,
                                secondMemberId,
                                FriendshipStatus.ACCEPTED
                        );

        boolean secondToFirst =
                friendshipRepository
                        .existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(
                                secondMemberId,
                                firstMemberId,
                                FriendshipStatus.ACCEPTED
                        );

        return firstToSecond || secondToFirst;
    }

    private void validateCollectionViewer(
            Long requesterId,
            Long ownerId
    ) {
        if (requesterId.equals(ownerId)) {
            return;
        }

        if (!isFriend(requesterId, ownerId)) {
            throw new CollectionItemAccessDeniedException();
        }
    }
}
