package com.anabada.fleaflea.fixture;

import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.item.domain.ItemStatus;
import com.anabada.fleaflea.domain.item.domain.ItemTradeType;
import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.member.domain.Member;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ItemFixture {

    public static Item createItem(Market market, Member seller, String title) {
        return createItem(market, seller, title, ItemTradeType.SALE, ItemStatus.AVAILABLE);
    }

    public static Item createItem(
            Market market,
            Member seller,
            String title,
            ItemTradeType tradeType,
            ItemStatus status
    ) {
        Item item = Item.create(
                null,
                market,
                seller,
                title,
                "test item description",
                tradeType,
                tradeType == ItemTradeType.SALE ? 10_000L : null,
                // 상품 이미지는 필수다. 조회는 키로 URL 문자열만 만들어 실제 파일은 필요 없다.
                "items/" + UUID.randomUUID() + ".png"
        );

        if (status != ItemStatus.AVAILABLE) {
            item.startTrade();
        }
        if (status == ItemStatus.COMPLETED) {
            item.completeTrade();
        }

        return item;
    }
}
