package com.anabada.fleaflea.domain.market.repository;

import com.anabada.fleaflea.domain.market.domain.Market;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketRepository extends JpaRepository<Market, Long> {

    boolean existsByInviteCode(String inviteCode);

    Optional<Market> findByInviteCode(String inviteCode);
}