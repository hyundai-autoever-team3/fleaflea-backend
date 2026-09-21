package com.anabada.fleaflea.domain.trade.repository;

import com.anabada.fleaflea.domain.trade.domain.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TradeRepository extends JpaRepository<Trade, Long> {
    boolean existsByCollectionTradeRequestId(Long collectionTradeRequestId);

    boolean existsByBegRequestId(Long begRequestId);

    Optional<Trade> findByTradeRequestId(Long tradeRequestId);

    Optional<Trade> findByCollectionTradeRequestId(
            Long collectionTradeRequestId
    );

    Optional<Trade> findByBegRequestId(Long begRequestId);
}
