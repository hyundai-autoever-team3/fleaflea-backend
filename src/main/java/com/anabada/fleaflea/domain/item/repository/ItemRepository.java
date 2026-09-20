package com.anabada.fleaflea.domain.item.repository;

import com.anabada.fleaflea.domain.item.domain.Item;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface ItemRepository extends JpaRepository<Item, Long>, ItemRepositoryCustom {

    // 상품 상세 조회 시 판매자 정보를 함께 조회
    @EntityGraph(attributePaths = "seller")
    Optional<Item> findWithSellerByItemId(Long itemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Item> findLockedByItemId(Long itemId);
}
