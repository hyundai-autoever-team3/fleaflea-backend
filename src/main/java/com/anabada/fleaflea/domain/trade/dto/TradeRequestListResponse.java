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
            TradeRequest request, MemberSummaryResponse owner, MemberSummaryResponse requester, String imageUrl
    ) {
        return new TradeRequestListResponse(
                "ITEM", request.getTradeRequestId(), request.getItem().getItemId(),
                request.getItem().getTitle(), request.getItem().getTradeType().name(),
                request.getStatus(), owner, requester, imageUrl, request.getCreatedAt()
        );
    }

    public static TradeRequestListResponse from(
            CollectionTradeRequest request, MemberSummaryResponse owner, MemberSummaryResponse requester, String imageUrl
    ) {
        return new TradeRequestListResponse(
                "COLLECTION", request.getCollectionTradeRequestId(), request.getCollectionItemSnapshotId(),
                request.getCollectionItemTitle(), request.getTradeType().name(),
                request.getStatus(), owner, requester, imageUrl, request.getCreatedAt()
        );
    }

    public static TradeRequestListResponse from(
            BegRequest request, MemberSummaryResponse owner, MemberSummaryResponse requester, String imageUrl
    ) {
        return new TradeRequestListResponse(
                "BEG", request.getBegRequestId(), request.getCollectionItemSnapshotId(),
                request.getCollectionItemTitle(), null, request.getStatus().toTradeRequestStatus(),
                owner, requester, imageUrl, request.getCreatedAt()
        );
    }
}
