package com.anabada.fleaflea.domain.market.repository;

import com.anabada.fleaflea.domain.market.domain.Market;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketRepository extends JpaRepository<Market, Long> {

    boolean existsByInviteCode(String inviteCode);

    Optional<Market> findByInviteCode(String inviteCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select market from Market market where market.marketId = :marketId")
    Optional<Market> findLockedById(@Param("marketId") Long marketId);

    @Query("select market.marketId from Market market where market.inviteCode = :inviteCode")
    Optional<Long> findMarketIdByInviteCode(@Param("inviteCode") String inviteCode);
}
