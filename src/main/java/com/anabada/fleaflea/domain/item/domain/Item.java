package com.anabada.fleaflea.domain.item.domain;

import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import com.anabada.fleaflea.domain.item.exception.*;
import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long itemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_item_id")
    private CollectionItem collectionItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Member seller;

    @Column(length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemTradeType tradeType;

    private Long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemStatus status;

    @Column(nullable = false)
    private String imageKey;

    @Builder
    private Item(CollectionItem collectionItem,
                Market market,
                Member seller,
                String title,
                String description,
                ItemTradeType tradeType,
                Long price,
                String imageKey) {
        this.collectionItem = collectionItem;
        this.market = market;
        this.seller = seller;
        this.title = title;
        this.description = description;
        this.tradeType = tradeType;
        this.price = price;
        this.status = ItemStatus.AVAILABLE;
        this.imageKey = imageKey;
    }

    public static Item create(CollectionItem collectionItem,
                              Market market,
                              Member seller,
                              String title,
                              String description,
                              ItemTradeType tradeType,
                              Long price,
                              String imageKey) {
        return Item.builder()
                .collectionItem(collectionItem)
                .market(market)
                .seller(seller)
                .title(title)
                .description(description)
                .tradeType(tradeType)
                .price(price)
                .imageKey(imageKey)
                .build();
    }

    public void update(String title,
                       String description,
                       ItemTradeType tradeType,
                       Long price,
                       String imageKey) {
        if (status == ItemStatus.COMPLETED) {
            throw new ItemAlreadyCompletedException();
        }

        if (title != null) this.title = title;

        if (description != null) this.description = description;

        if (tradeType != null) {
            this.tradeType = tradeType;

            if (tradeType == ItemTradeType.GIVEAWAY) {
                this.price = null;
            } else if (price != null) {
                this.price = price;
            }
        } else if (price != null) {
            this.price = price;
        }

        this.imageKey = imageKey;
    }

    public void validateOwner(Long memberId) {
        if (!seller.getMemberId().equals(memberId)) {
            throw new ItemNotOwnerException();
        }
    }

    public void validateDeletable() {
        if (status == ItemStatus.IN_PROGRESS) {
            throw new ItemTradeInProgressException();
        }

        if (status == ItemStatus.COMPLETED) {
            throw new ItemAlreadyCompletedException();
        }
    }

    public void startTrade() {
        if (status != ItemStatus.AVAILABLE) {
            throw new ItemNotAvailableException();
        }

        this.status = ItemStatus.IN_PROGRESS;
    }

    public void completeTrade() {
        if (status != ItemStatus.IN_PROGRESS) {
            throw new ItemTradeNotInProgressException();
        }

        this.status = ItemStatus.COMPLETED;
    }
}
