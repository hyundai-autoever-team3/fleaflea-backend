package com.anabada.fleaflea.domain.collectionitem.service;

import com.anabada.fleaflea.domain.collectionitem.domain.CollectionItem;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemCreateRequest;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemResponse;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemSummaryResponse;
import com.anabada.fleaflea.domain.collectionitem.dto.CollectionItemUpdateRequest;
import com.anabada.fleaflea.domain.collectionitem.exception.CollectionItemAccessDeniedException;
import com.anabada.fleaflea.domain.collectionitem.exception.CollectionItemNotFoundException;
import com.anabada.fleaflea.domain.collectionitem.repository.CollectionItemRepository;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionItemService {

    private final CollectionItemRepository collectionItemRepository;
    private final MemberRepository memberRepository;
    private final ImageService imageService;

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
            Pageable pageable
    ) {
        Member owner = getMember(memberId);

        return PageResponse.from(
                collectionItemRepository
                        .findAllByOwner(owner, pageable)
                        .map(this::toSummaryResponse)
        );
    }

    public PageResponse<CollectionItemSummaryResponse> getMemberCollectionItems(
            Long requesterId,
            Long ownerId,
            Pageable pageable
    ) {
        getMember(requesterId);
        Member owner = getMember(ownerId);

        return PageResponse.from(
                collectionItemRepository
                        .findAllByOwnerAndIsPublicTrue(
                                owner,
                                pageable
                        )
                        .map(this::toSummaryResponse)
        );
    }

    public CollectionItemResponse getCollectionItem(
            Long memberId,
            Long collectionItemId
    ) {
        getMember(memberId);

        CollectionItem collectionItem =
                getCollectionItem(collectionItemId);

        boolean owner = collectionItem.isOwnedBy(memberId);
        boolean publicItem =
                Boolean.TRUE.equals(collectionItem.getIsPublic());

        if (!owner && !publicItem) {
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

        String imageKey = collectionItem.getImageKey();

        collectionItemRepository.delete(collectionItem);

        if (imageKey != null) {
            imageService.delete(imageKey);
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
                imageUrl
        );
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
}