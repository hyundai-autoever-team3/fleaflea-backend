package com.anabada.fleaflea.domain.trade.repository;

import com.anabada.fleaflea.domain.trade.domain.CollectionTradeType;
import com.anabada.fleaflea.domain.trade.domain.Trade;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface TradeRepository extends JpaRepository<Trade, Long> {
    @Modifying(flushAutomatically = true)
    @Query("""
            delete from Trade trade
            where trade.itemId = :itemId
               or trade.tradeRequestId in (
                   select request.tradeRequestId from TradeRequest request
                   where request.item.itemId = :itemId
               )
            """)
    void deleteAllRelatedToItem(@Param("itemId") Long itemId);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from Trade trade
            where trade.collectionTradeRequestId in (
                select request.collectionTradeRequestId
                from CollectionTradeRequest request
                where (request.collectionItem.collectionItemId = :collectionItemId
                    or request.offerCollectionItem.collectionItemId = :collectionItemId)
                  and (request.status in :statuses
                    or (request.status = :completedStatus and request.tradeType = :rentalType))
            )
            """)
    void deleteAllByDeletableCollectionRequests(
            @Param("collectionItemId") Long collectionItemId,
            @Param("statuses") Collection<TradeRequestStatus> statuses,
            @Param("completedStatus") TradeRequestStatus completedStatus,
            @Param("rentalType") CollectionTradeType rentalType
    );

    boolean existsByCollectionTradeRequestId(Long collectionTradeRequestId);

    boolean existsByBegRequestId(Long begRequestId);

    Optional<Trade> findByTradeRequestId(Long tradeRequestId);

    Optional<Trade> findByCollectionTradeRequestId(
            Long collectionTradeRequestId
    );

    Optional<Trade> findByBegRequestId(Long begRequestId);
}
