package com.anabada.fleaflea.domain.market.dto;

import com.anabada.fleaflea.domain.market.domain.Market;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record MarketDetailResponse(

        @Schema(description = "플리마켓 ID", example = "1")
        Long marketId,

        @Schema(description = "개설자 회원 ID", example = "2")
        Long hostId,

        @Schema(description = "개설자 닉네임", example = "홍길동")
        String hostNickname,

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
            String coverImageUrl
    ) {
        return new MarketDetailResponse(
                market.getMarketId(),
                market.getHost().getMemberId(),
                market.getHost().getNickname(),
                market.getTitle(),
                market.getDescription(),
                coverImageUrl,
                memberCount,
                market.getCreatedAt()
        );
    }
}
