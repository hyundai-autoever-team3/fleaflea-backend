package com.anabada.fleaflea.domain.market.dto;

import com.anabada.fleaflea.domain.market.domain.Market;
import io.swagger.v3.oas.annotations.media.Schema;

public record MarketInvitationResponse(

        @Schema(description = "플리마켓 ID", example = "1")
        Long marketId,

        @Schema(description = "초대 코드", example = "A7F233913E")
        String inviteCode
) {

    public static MarketInvitationResponse from(Market market) {
        return new MarketInvitationResponse(market.getMarketId(), market.getInviteCode());
    }
}