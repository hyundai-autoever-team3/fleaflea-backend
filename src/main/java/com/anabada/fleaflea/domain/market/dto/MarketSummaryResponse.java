package com.anabada.fleaflea.domain.market.dto;

import com.anabada.fleaflea.domain.market.domain.Market;
import com.anabada.fleaflea.domain.marketmember.domain.MarketMember;
import com.anabada.fleaflea.domain.member.dto.MemberSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record MarketSummaryResponse(

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

        @Schema(description = "플리마켓 참여 시각")
        LocalDateTime joinedAt
) {

    public static MarketSummaryResponse from(
            MarketMember marketMember,
            String coverImageUrl,
            MemberSummaryResponse host
    ) {
        Market market = marketMember.getMarket();

        return new MarketSummaryResponse(
                market.getMarketId(),
                host,
                market.getTitle(),
                market.getDescription(),
                coverImageUrl,
                marketMember.getJoinedAt()
        );
    }
}