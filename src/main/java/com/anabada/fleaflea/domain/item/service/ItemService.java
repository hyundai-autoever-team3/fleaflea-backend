package com.anabada.fleaflea.domain.item.service;

import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import com.anabada.fleaflea.domain.collection.exception.CollectionItemNotFoundException;
import com.anabada.fleaflea.domain.collection.exception.CollectionItemNotOwnerException;
import com.anabada.fleaflea.domain.collection.repository.CollectionItemRepository;
import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.item.domain.ItemStatus;
import com.anabada.fleaflea.domain.item.domain.ItemTradeType;
import com.anabada.fleaflea.domain.item.dto.ItemCreateRequest;
import com.anabada.fleaflea.domain.item.dto.ItemDetailResponse;
import com.anabada.fleaflea.domain.item.dto.ItemSummaryResponse;
import com.anabada.fleaflea.domain.item.dto.ItemUpdateRequest;
import com.anabada.fleaflea.domain.item.dto.SellerResponse;
import com.anabada.fleaflea.domain.item.exception.ItemImageRequiredException;
import com.anabada.fleaflea.domain.item.exception.ItemNotFoundException;
import com.anabada.fleaflea.domain.item.repository.ItemRepository;
import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.market.exception.MarketNotFoundException;
import com.anabada.fleaflea.domain.market.exception.MarketNotParticipantException;
import com.anabada.fleaflea.domain.market.repository.MarketRepository;
import com.anabada.fleaflea.domain.marketmember.repository.MarketMemberRepository;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.global.dto.PageResponse;
import com.anabada.fleaflea.global.image.ImageCategory;
import com.anabada.fleaflea.global.image.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

    private final ItemRepository itemRepository;
    private final MarketRepository marketRepository;
    private final MarketMemberRepository marketMemberRepository;
    private final CollectionItemRepository collectionItemRepository;
    private final MemberRepository memberRepository;
    private final ImageService imageService;

    @Transactional
    public ItemSummaryResponse createItem(
            Long marketId,
            Long memberId,
            ItemCreateRequest request
    ) {
        Market market = marketRepository.findById(marketId)
                .orElseThrow(MarketNotFoundException::new);

        validateMarketParticipant(marketId, memberId);

        CollectionItem collectionItem = null;

        if (request.collectionItemId() != null) {
            collectionItem = collectionItemRepository.findById(request.collectionItemId())
                    .orElseThrow(CollectionItemNotFoundException::new);

            validateCollectionItemOwner(collectionItem, memberId);
        }

        Member seller = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        String imageKey = resolveImageKey(
                request,
                collectionItem
        );

        Long price = request.tradeType() == ItemTradeType.SALE
                ? request.price()
                : null;

        Item item = Item.create(
                collectionItem,
                market,
                seller,
                request.title(),
                request.description(),
                request.tradeType(),
                price,
                imageKey
        );

        return ItemSummaryResponse.of(itemRepository.save(item), imageService.getUrl(imageKey));
    }

    public ItemDetailResponse findItem(
            Long itemId,
            Long memberId
    ) {
        Item item = itemRepository.findWithSellerByItemId(itemId)
                .orElseThrow(ItemNotFoundException::new);

        validateMarketParticipant(
                item.getMarket().getMarketId(),
                memberId
        );

        Member seller = item.getSeller();

        return ItemDetailResponse.of(
                item,
                SellerResponse.from(seller, imageService.getUrl(seller.getProfileImageKey()))
        );
    }

    public PageResponse<ItemSummaryResponse> findItems(
            Long marketId,
            Long memberId,
            int page,
            int size,
            ItemTradeType tradeType,
            ItemStatus status,
            String keyword
    ) {
        validateMarketExists(marketId);
        validateMarketParticipant(marketId, memberId);

        // 정렬(최신 등록순)은 인덱스와 맞춰 리포지토리 쿼리에 고정되어 있다.
        PageRequest pageable = PageRequest.of(page, size);

        Page<ItemSummaryResponse> items = itemRepository
                .searchInMarket(
                        marketId,
                        tradeType,
                        status,
                        keyword,
                        pageable
                )
                .map(item -> ItemSummaryResponse.of(
                        item,
                        imageService.getUrl(item.getImageKey())
                ));

        return PageResponse.from(items);
    }

    @Transactional
    public ItemDetailResponse updateItem(
            Long itemId,
            Long memberId,
            ItemUpdateRequest request
    ) {
        Item item = itemRepository.findWithSellerByItemId(itemId)
                .orElseThrow(ItemNotFoundException::new);

        item.validateOwner(memberId);

        String imageKey = item.getImageKey();

        if (request.image() != null && !request.image().isEmpty()) {
            if (imageKey == null) {
                imageKey = imageService.upload(
                        request.image(),
                        ImageCategory.ITEM
                );
            } else {
                imageKey = imageService.replace(
                        imageKey,
                        request.image(),
                        ImageCategory.ITEM
                );
            }
        }

        item.update(
                request.title(),
                request.description(),
                request.tradeType(),
                request.price(),
                imageKey
        );

        Member seller = item.getSeller();

        return ItemDetailResponse.of(
                item,
                SellerResponse.from(
                        seller,
                        imageService.getUrl(seller.getProfileImageKey())
                )
        );
    }

    @Transactional
    public void deleteItem(
            Long itemId,
            Long memberId
    ) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(ItemNotFoundException::new);

        item.validateOwner(memberId);
        item.validateDeletable();

        String imageKey = item.getImageKey();

        itemRepository.delete(item);

        imageService.delete(imageKey);
    }

    private void validateMarketParticipant(
            Long marketId,
            Long memberId
    ) {
        if (!marketMemberRepository
                .existsByMarket_MarketIdAndMember_MemberId(marketId, memberId)) {
            throw new MarketNotParticipantException();
        }
    }

    private void validateCollectionItemOwner(
            CollectionItem collectionItem,
            Long memberId
    ) {
        if (!collectionItem.isOwnedBy(memberId)) {
            throw new CollectionItemNotOwnerException();
        }
    }

    private void validateMarketExists(Long marketId) {
        if (!marketRepository.existsById(marketId)) {
            throw new MarketNotFoundException();
        }
    }

    private String resolveImageKey(
            ItemCreateRequest request,
            CollectionItem collectionItem
    ) {
        if (request.image() != null && !request.image().isEmpty()) {
            return imageService.upload(
                    request.image(),
                    ImageCategory.ITEM
            );
        }

        if (collectionItem != null && collectionItem.getImageKey() != null) {
            return imageService.copy(
                    collectionItem.getImageKey(),
                    ImageCategory.ITEM
            );
        }

        throw new ItemImageRequiredException();
    }
}
