package com.anabada.fleaflea.domain.trade.repository;

import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeRequest;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Collection;

import java.util.List;
import java.util.Optional;

public interface CollectionTradeRequestRepository extends JpaRepository<CollectionTradeRequest, Long> {

    boolean existsByRequesterAndCollectionItemAndStatusIn(
            Member requester,
            CollectionItem collectionItem,
            Collection<TradeRequestStatus> statuses
    );

    @EntityGraph(attributePaths = {"collectionItem", "collectionItem.owner", "requester", "offerCollectionItem"})
    Optional<CollectionTradeRequest> findWithDetailsByCollectionTradeRequestId(Long collectionTradeRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CollectionTradeRequest> findLockedByCollectionTradeRequestId(Long collectionTradeRequestId);

    @EntityGraph(attributePaths = {"collectionItem", "collectionItem.owner", "requester", "offerCollectionItem"})
    List<CollectionTradeRequest> findByCollectionItem_Owner_MemberIdOrderByCreatedAtDesc(Long ownerId);

    @EntityGraph(attributePaths = {"collectionItem", "collectionItem.owner", "requester", "offerCollectionItem"})
    List<CollectionTradeRequest> findByRequester_MemberIdOrderByCreatedAtDesc(Long requesterId);
}
