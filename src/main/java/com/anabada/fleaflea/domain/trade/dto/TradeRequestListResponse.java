package com.anabada.fleaflea.domain.trade.dto;

import com.anabada.fleaflea.domain.member.dto.MemberSummaryResponse;
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
        LocalDateTime createdAt
) {
}
