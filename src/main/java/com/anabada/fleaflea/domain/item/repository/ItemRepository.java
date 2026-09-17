package com.anabada.fleaflea.domain.item.repository;

import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.item.domain.ItemStatus;
import com.anabada.fleaflea.domain.item.domain.ItemTradeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, Long> {
    // 상품 목록 조회 - 거래 유형, 상품 상태, 검색어 조건 적용
    @Query("""
            select i from Item i
            where i.market.marketId = :marketId
              and (:tradeType is null or i.tradeType = :tradeType)
              and (:status is null or i.status = :status)
              and lower(i.title) like lower(concat('%', coalesce(:keyword, ''), '%'))
            """)
    Page<Item> findAllByMarketId(
            @Param("marketId") Long marketId,
            @Param("tradeType") ItemTradeType tradeType,
            @Param("status") ItemStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    // 상품 상세 조회 시 판매자 정보를 함께 조회
    @EntityGraph(attributePaths = "seller")
    Optional<Item> findWithSellerByItemId(Long itemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Item> findLockedByItemId(Long itemId);
}
