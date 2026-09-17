package com.anabada.fleaflea.domain.trade.repository;

import com.anabada.fleaflea.domain.trade.domain.CollectionTradeRequest;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
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
    Optional<CollectionTradeRequest> findById(Long requestId);

    boolean existsByTargetItem_CollectionItemIdAndRequester_MemberIdAndStatusIn(
            Long collectionItemId,
            Long requesterId,
            Collection<TradeRequestStatus> statuses
    );

    // 내가 받은 거래 요청 목록 (아이템 소유자가 나인 경우)
    @EntityGraph(attributePaths = {
            "targetItem",
            "targetItem.owner",
            "requester",
            "offerItem"
    })
    List<CollectionTradeRequest> findByTargetItem_Owner_MemberIdOrderByCreatedAtDesc(Long ownerId);

    // 내가 보낸 거래 요청 목록 (요청자가 나인 경우)
    @EntityGraph(attributePaths = {
            "targetItem",
            "targetItem.owner",
            "requester",
            "offerItem"
    })
    List<CollectionTradeRequest> findByRequester_MemberIdOrderByCreatedAtDesc(Long requesterId);
}