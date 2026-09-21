package com.anabada.fleaflea.domain.trade.repository;

import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import com.anabada.fleaflea.domain.member.domain.Member;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeRequest;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeType;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Collection;

import java.util.List;
import java.util.Optional;

public interface CollectionTradeRequestRepository extends JpaRepository<CollectionTradeRequest, Long> {

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from CollectionTradeRequest request
            where (
                request.collectionItem.collectionItemId = :collectionItemId
                or request.offerCollectionItem.collectionItemId = :collectionItemId
            )
            and (
                request.status in :statuses
                or (
                    request.status = :completedStatus
                    and request.tradeType = :rentalType
                )
            )
            """)
    void deleteAllDeletableByCollectionItemId(
            @Param("collectionItemId") Long collectionItemId,
            @Param("statuses") Collection<TradeRequestStatus> statuses,
            @Param("completedStatus") TradeRequestStatus completedStatus,
            @Param("rentalType") CollectionTradeType rentalType
    );

    boolean existsByRequesterAndCollectionItemAndStatusIn(
            Member requester,
            CollectionItem collectionItem,
            Collection<TradeRequestStatus> statuses
    );

    boolean existsByCollectionItem_CollectionItemIdAndStatus(
            Long collectionItemId,
            TradeRequestStatus status
    );

    @EntityGraph(attributePaths = {"collectionItem", "collectionItem.owner", "requester", "owner", "offerCollectionItem"})
    Optional<CollectionTradeRequest> findWithDetailsByCollectionTradeRequestId(Long collectionTradeRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CollectionTradeRequest> findLockedByCollectionTradeRequestId(Long collectionTradeRequestId);

    @EntityGraph(attributePaths = {"collectionItem", "collectionItem.owner", "requester", "owner", "offerCollectionItem"})
    List<CollectionTradeRequest> findByOwner_MemberIdOrderByCreatedAtDesc(Long ownerId);

    @EntityGraph(attributePaths = {"collectionItem", "collectionItem.owner", "requester", "owner", "offerCollectionItem"})
    List<CollectionTradeRequest> findByRequester_MemberIdOrderByCreatedAtDesc(Long requesterId);
}
