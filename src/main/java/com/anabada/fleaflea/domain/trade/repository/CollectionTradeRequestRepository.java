package com.anabada.fleaflea.domain.trade.repository;

import com.anabada.fleaflea.domain.trade.domain.CollectionTradeRequest;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface CollectionTradeRequestRepository
        extends JpaRepository<CollectionTradeRequest, Long> {

    @Override
    @EntityGraph(attributePaths = {
            "targetItem",
            "targetItem.owner",
            "requester",
            "offerItem"
    })
    Optional<CollectionTradeRequest> findById(
            Long requestId
    );

    boolean existsByTargetItem_CollectionItemIdAndRequester_MemberIdAndStatusIn(
            Long collectionItemId,
            Long requesterId,
            Collection<TradeRequestStatus> statuses
    );
}