package com.anabada.fleaflea.domain.market.dto;

import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.member.dto.MemberSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record MarketDetailResponse(

        @Schema(description = "플리마켓 ID", example = "1")
        Long marketId,

        @Schema(description = "개설자 회원 정보")
        MemberSummaryResponse host,

        @Schema(description = "플리마켓 이름", example = "우리 동네 플리마켓")
        String title,

        @Schema(description = "플리마켓 설명")
        String description,

        @Schema(description = "플리마켓 커버 이미지 URL")
        String coverImageUrl,

        @Schema(description = "참여자 수", example = "3")
        long memberCount,

        @Schema(description = "플리마켓 생성 시각")
        LocalDateTime createdAt
) {

    public static MarketDetailResponse from(
            Market market,
            long memberCount,
            String coverImageUrl,
            MemberSummaryResponse host
    ) {
        return new MarketDetailResponse(
                market.getMarketId(),
                host,
                market.getTitle(),
                market.getDescription(),
                coverImageUrl,
                memberCount,
                market.getCreatedAt()
        );
    }
}