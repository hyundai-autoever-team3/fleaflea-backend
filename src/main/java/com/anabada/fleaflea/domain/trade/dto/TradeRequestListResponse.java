package com.anabada.fleaflea.domain.trade.dto;

import com.anabada.fleaflea.domain.begrequest.domain.BegRequest;
import com.anabada.fleaflea.domain.member.dto.MemberSummaryResponse;
import com.anabada.fleaflea.domain.trade.domain.CollectionTradeRequest;
import com.anabada.fleaflea.domain.trade.domain.TradeRequest;
import com.anabada.fleaflea.domain.trade.domain.TradeRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "상품 또는 도감 거래 요청 목록 항목. requestType과 requestId로 상세 API를 선택합니다.")
public record TradeRequestListResponse(
        String requestType,
        Long requestId,
        Long targetItemId,
        String targetItemTitle,
        String tradeType,
        TradeRequestStatus status,
        MemberSummaryResponse owner,
        MemberSummaryResponse requester,
        String imageUrl,
        LocalDateTime createdAt
) {

    public static TradeRequestListResponse from(
            TradeRequest tradeRequest,
            MemberSummaryResponse owner,
            MemberSummaryResponse requester,
            String imageUrl
    ) {
        return new TradeRequestListResponse(
                "ITEM", tradeRequest.getTradeRequestId(), tradeRequest.getItem().getItemId(),
                tradeRequest.getItem().getTitle(), tradeRequest.getItem().getTradeType().name(),
                tradeRequest.getStatus(), owner, requester, imageUrl, tradeRequest.getCreatedAt()
        );
    }

    public static TradeRequestListResponse from(
            CollectionTradeRequest collectionTradeRequest,
            MemberSummaryResponse owner,
            MemberSummaryResponse requester,
            String imageUrl
    ) {
        return new TradeRequestListResponse(
                "COLLECTION", collectionTradeRequest.getCollectionTradeRequestId(), collectionTradeRequest.getCollectionItemSnapshotId(),
                collectionTradeRequest.getCollectionItemTitle(), collectionTradeRequest.getTradeType().name(),
                collectionTradeRequest.getStatus(), owner, requester, imageUrl, collectionTradeRequest.getCreatedAt()
        );
    }

    public static TradeRequestListResponse from(
            BegRequest begRequest,
            MemberSummaryResponse owner,
            MemberSummaryResponse requester,
            String imageUrl
    ) {
        return new TradeRequestListResponse(
                "BEG", begRequest.getBegRequestId(), begRequest.getCollectionItemSnapshotId(),
                begRequest.getCollectionItemTitle(), null, begRequest.getStatus().toTradeRequestStatus(),
                owner, requester, imageUrl, begRequest.getCreatedAt()
        );
    }
}
