package com.anabada.fleaflea.domain.collectionitem.service;

import com.anabada.fleaflea.domain.begrequest.domain.BegRequestStatus;
import com.anabada.fleaflea.domain.begrequest.repository.BegRequestRepository;
import com.anabada.fleaflea.domain.collectionitem.domain.CollectionItem;
import com.anabada.fleaflea.domain.collectionitem.domain.CollectionItemStatus;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemCreateRequest;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemResponse;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemSearchCondition;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemSummaryResponse;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemUpdateRequest;
import com.anabada.fleaflea.domain.collectionitem.exception.CollectionItemAccessDeniedException;
import com.anabada.fleaflea.domain.collectionitem.exception.CollectionItemNotFoundException;
import com.anabada.fleaflea.domain.collectionitem.exception.CollectionItemTradeInProgressException;
import com.anabada.fleaflea.domain.collectionitem.repository.CollectionItemRepository;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.friendship.repository.FriendshipRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeType;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import com.anabada.fleaflea.domain.trade.repository.CollectionTradeRequestRepository;
import com.anabada.fleaflea.domain.trade.repository.TradeRequestRepository;
import com.anabada.fleaflea.global.dto.PageResponse;
import com.anabada.fleaflea.global.image.ImageCategory;
import com.anabada.fleaflea.global.image.ImageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionItemService {

    private static final List<BegRequestStatus> BLOCKING_BEG_STATUSES =
            List.of(BegRequestStatus.PENDING, BegRequestStatus.ACCEPTED);
    private static final List<TradeRequestStatus> BLOCKING_TRADE_STATUSES =
            List.of(TradeRequestStatus.PENDING, TradeRequestStatus.ACCEPTED);
    private static final List<BegRequestStatus> DELETABLE_BEG_STATUSES =
            List.of(BegRequestStatus.REJECTED, BegRequestStatus.CANCELLED);
    private static final List<TradeRequestStatus> DELETABLE_TRADE_STATUSES =
            List.of(TradeRequestStatus.REJECTED, TradeRequestStatus.CANCELLED);

    private final CollectionItemRepository collectionItemRepository;
    private final MemberRepository memberRepository;
    private final ImageService imageService;
    private final FriendshipRepository friendshipRepository;
    private final BegRequestRepository begRequestRepository;
    private final CollectionTradeRequestRepository collectionTradeRequestRepository;
    private final TradeRequestRepository tradeRequestRepository;

    @Transactional
    public CollectionItemResponse createCollectionItem(Long memberId, CollectionItemCreateRequest request) {
        Member owner = getMember(memberId);
        String imageKey = imageService.uploadInTransaction(request.image(), ImageCategory.COLLECTION_ITEM);
        CollectionItem collectionItem = CollectionItem.create(
                owner, request.title(), request.description(), imageKey, request.isPublic()
        );
        CollectionItem savedItem = collectionItemRepository.save(collectionItem);

        return toCollectionItemResponse(savedItem);
    }

    public PageResponse<CollectionItemSummaryResponse> getMyCollectionItems(
            Long memberId, CollectionItemSearchCondition condition, Pageable pageable
    ) {
        Member owner = getMember(memberId);

        return PageResponse.from(collectionItemRepository.search(owner.getMemberId(), false, condition, pageable)
                .map(this::toCollectionItemSummaryResponse));
    }

    public PageResponse<CollectionItemSummaryResponse> getMemberCollectionItems(
            Long requesterId, Long ownerId, CollectionItemSearchCondition condition, Pageable pageable
    ) {
        Member requester = getMember(requesterId);
        Member owner = getMember(ownerId);
        validateCollectionViewer(requester.getMemberId(), owner.getMemberId());

        return PageResponse.from(collectionItemRepository.search(owner.getMemberId(), true, condition, pageable)
                .map(this::toCollectionItemSummaryResponse));
    }

    public CollectionItemResponse getCollectionItem(Long memberId, Long collectionItemId) {
        Member requester = getMember(memberId);
        CollectionItem collectionItem = findCollectionItem(collectionItemId);

        if (!collectionItem.isOwnedBy(requester.getMemberId())) {
            if (!Boolean.TRUE.equals(collectionItem.getIsPublic())
                    || !isAcceptedFriend(requester.getMemberId(), collectionItem.getOwner().getMemberId())) {
                throw new CollectionItemAccessDeniedException();
            }
        }

        return toCollectionItemResponse(collectionItem);
    }

    @Transactional
    public CollectionItemResponse updateCollectionItem(
            Long memberId, Long collectionItemId, CollectionItemUpdateRequest request
    ) {
        CollectionItem collectionItem = findLockedCollectionItem(collectionItemId);
        validateOwner(collectionItem, memberId);
        collectionItem.update(request.title(), request.description(), request.isPublic());

        if (request.image() != null && !request.image().isEmpty()) {
            String previousImageKey = collectionItem.getImageKey();
            String newImageKey = previousImageKey == null
                    ? imageService.uploadInTransaction(request.image(), ImageCategory.COLLECTION_ITEM)
                    : imageService.replace(previousImageKey, request.image(), ImageCategory.COLLECTION_ITEM);
            collectionItem.updateImageKey(newImageKey);
        }

        return toCollectionItemResponse(collectionItem);
    }

    @Transactional
    public void deleteCollectionItem(Long memberId, Long collectionItemId) {
        CollectionItem collectionItem = findLockedCollectionItem(collectionItemId);
        validateOwner(collectionItem, memberId);
        validateDeletable(collectionItemId);

        collectionTradeRequestRepository.deleteAllDeletableByCollectionItemId(
                collectionItemId, DELETABLE_TRADE_STATUSES, TradeRequestStatus.COMPLETED, CollectionTradeType.RENTAL
        );
        begRequestRepository.deleteAllByCollectionItemIdAndStatusIn(collectionItemId, DELETABLE_BEG_STATUSES);
        collectionItemRepository.delete(collectionItem);
        imageService.deleteAfterCommit(collectionItem.getImageKey());
    }

    private void validateDeletable(Long collectionItemId) {
        boolean active = begRequestRepository.existsByCollectionItem_CollectionItemIdAndStatusIn(
                collectionItemId, BLOCKING_BEG_STATUSES
        ) || collectionTradeRequestRepository.existsByCollectionItem_CollectionItemIdAndStatusIn(
                collectionItemId, BLOCKING_TRADE_STATUSES
        ) || collectionTradeRequestRepository.existsByOfferCollectionItem_CollectionItemIdAndStatusIn(
                collectionItemId, BLOCKING_TRADE_STATUSES
        ) || tradeRequestRepository.existsByItem_CollectionItem_CollectionItemIdAndStatusIn(
                collectionItemId, BLOCKING_TRADE_STATUSES
        );

        if (active) {
            throw new CollectionItemTradeInProgressException();
        }
    }

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(MemberNotFoundException::new);
    }

    private CollectionItem findCollectionItem(Long collectionItemId) {
        return collectionItemRepository.findById(collectionItemId).orElseThrow(CollectionItemNotFoundException::new);
    }

    private CollectionItem findLockedCollectionItem(Long collectionItemId) {
        return collectionItemRepository.findLockedById(collectionItemId).orElseThrow(CollectionItemNotFoundException::new);
    }

    private void validateOwner(CollectionItem collectionItem, Long memberId) {
        if (!collectionItem.isOwnedBy(memberId)) {
            throw new CollectionItemAccessDeniedException();
        }
    }

    private CollectionItemResponse toCollectionItemResponse(CollectionItem collectionItem) {
        return CollectionItemResponse.from(
                collectionItem, imageService.getUrl(collectionItem.getImageKey()), getCollectionItemStatus(collectionItem)
        );
    }

    private CollectionItemStatus getCollectionItemStatus(CollectionItem collectionItem) {
        Long collectionItemId = collectionItem.getCollectionItemId();
        boolean inProgress = begRequestRepository.existsByCollectionItem_CollectionItemIdAndStatus(
                collectionItemId, BegRequestStatus.ACCEPTED
        ) || collectionTradeRequestRepository.existsByCollectionItem_CollectionItemIdAndStatus(
                collectionItemId, TradeRequestStatus.ACCEPTED
        ) || collectionTradeRequestRepository.existsByOfferCollectionItem_CollectionItemIdAndStatus(
                collectionItemId, TradeRequestStatus.ACCEPTED
        ) || tradeRequestRepository.existsByItem_CollectionItem_CollectionItemIdAndStatus(
                collectionItemId, TradeRequestStatus.ACCEPTED
        );

        return inProgress ? CollectionItemStatus.IN_PROGRESS : CollectionItemStatus.AVAILABLE;
    }

    private CollectionItemSummaryResponse toCollectionItemSummaryResponse(CollectionItem collectionItem) {
        return CollectionItemSummaryResponse.from(collectionItem, imageService.getUrl(collectionItem.getImageKey()));
    }

    private boolean isAcceptedFriend(Long memberId, Long friendId) {
        return friendshipRepository.existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(
                memberId, friendId, FriendshipStatus.ACCEPTED
        ) || friendshipRepository.existsByRequester_MemberIdAndAddressee_MemberIdAndStatus(
                friendId, memberId, FriendshipStatus.ACCEPTED
        );
    }

    private void validateCollectionViewer(Long requesterId, Long ownerId) {
        if (!requesterId.equals(ownerId) && !isAcceptedFriend(requesterId, ownerId)) {
            throw new CollectionItemAccessDeniedException();
        }
    }
}
