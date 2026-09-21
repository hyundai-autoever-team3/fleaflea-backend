package com.anabada.fleaflea.domain.trade.repository;

import com.anabada.fleaflea.domain.trade.domain.TradeRequest;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TradeRequestRepository extends JpaRepository<TradeRequest, Long> {

    @EntityGraph(attributePaths = {"item", "item.seller", "requester"})
    List<TradeRequest> findByItem_Seller_MemberIdOrderByCreatedAtDesc(Long sellerId);

    @EntityGraph(attributePaths = {"item", "item.seller", "requester"})
    List<TradeRequest> findByRequester_MemberIdOrderByCreatedAtDesc(Long requesterId);

    @EntityGraph(attributePaths = {"item", "item.seller", "requester"})
    Optional<TradeRequest> findWithDetailsByTradeRequestId(Long tradeRequestId);

    @Query(
            value = """
                    select tradeRequest
                    from TradeRequest tradeRequest
                    join fetch tradeRequest.item item
                    join fetch item.seller seller
                    join fetch tradeRequest.requester requester
                    where (
                        requester.memberId = :memberId
                        or seller.memberId = :memberId
                    )
                    and tradeRequest.status in :statuses
                    """,
            countQuery = """
                    select count(tradeRequest)
                    from TradeRequest tradeRequest
                    where (
                        tradeRequest.requester.memberId = :memberId
                        or tradeRequest.item.seller.memberId = :memberId
                    )
                    and tradeRequest.status in :statuses
                    """
    )
    Page<TradeRequest> findAllByParticipantIdAndStatusIn(
            @Param("memberId") Long memberId,
            @Param("statuses") Collection<TradeRequestStatus> statuses,
            Pageable pageable
    );

    boolean existsByItem_ItemIdAndRequester_MemberIdAndStatus(
            Long itemId,
            Long memberId,
            TradeRequestStatus status
    );

    @Query("""
        select tradeRequest
        from TradeRequest tradeRequest
        join fetch tradeRequest.requester
        where tradeRequest.item.itemId = :itemId
          and tradeRequest.tradeRequestId <> :acceptedRequestId
          and tradeRequest.status = :pendingStatus
        """)
    List<TradeRequest> findPendingRequestsToAutoReject(
            @Param("itemId") Long itemId,
            @Param("acceptedRequestId") Long acceptedRequestId,
            @Param("pendingStatus") TradeRequestStatus pendingStatus
    );

    @Modifying
    @Query("""
        update TradeRequest tradeRequest
        set tradeRequest.status = :rejectedStatus
        where tradeRequest.item.itemId = :itemId
          and tradeRequest.tradeRequestId <> :acceptedRequestId
          and tradeRequest.status = :pendingStatus
        """)
    void rejectOtherPendingRequests(
            @Param("itemId") Long itemId,
            @Param("acceptedRequestId") Long acceptedRequestId,
            @Param("pendingStatus") TradeRequestStatus pendingStatus,
            @Param("rejectedStatus") TradeRequestStatus rejectedStatus
    );

    @Query("""
        select tr.item.itemId
        from TradeRequest tr
        where tr.tradeRequestId = :requestId
        """)
    Optional<Long> findItemIdByTradeRequestId(Long requestId);
}
