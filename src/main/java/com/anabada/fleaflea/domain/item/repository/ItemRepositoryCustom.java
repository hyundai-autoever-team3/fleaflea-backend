package com.anabada.fleaflea.domain.item.repository;

import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.item.domain.ItemStatus;
import com.anabada.fleaflea.domain.item.domain.ItemTradeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ItemRepositoryCustom {
    Page<Item> searchInMarket(
            Long marketId,
            ItemTradeType tradeType,
            ItemStatus status,
            String keyword,
            Pageable pageable
    );
}
