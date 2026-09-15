package com.anabada.fleaflea.domain.market.dto;

import com.anabada.fleaflea.domain.market.domain.Market;
import io.swagger.v3.oas.annotations.media.Schema;

public record MarketCreateResponse(

        @Schema(description = "플리마켓 ID", example = "1")
        Long marketId,

        @Schema(description = "개설자 회원 ID", example = "2")
        Long hostId,

        @Schema(description = "플리마켓 이름", example = "우리 동네 플리마켓")
        String title,

        @Schema(description = "플리마켓 설명")
        String description,

        @Schema(description = "플리마켓 커버 이미지 경로")
        String coverImageUrl,

        @Schema(description = "플리마켓 초대 코드", example = "A7F233913E")
        String inviteCode
) {

    public static MarketCreateResponse from(Market market) {
        return new MarketCreateResponse(
                market.getMarketId(),
                market.getHost().getMemberId(),
                market.getTitle(),
                market.getDescription(),
                market.getCoverImageUrl(),
                market.getInviteCode()
        );
    }
}