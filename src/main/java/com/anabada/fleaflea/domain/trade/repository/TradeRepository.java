package com.anabada.fleaflea.domain.trade.repository;

import com.anabada.fleaflea.domain.trade.domain.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<Trade, Long> {
}