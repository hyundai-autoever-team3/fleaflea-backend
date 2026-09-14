package com.anabada.fleaflea.domain.marketmember.dto;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

public record MarketJoinRequest(

        @NotBlank(message = "초대 코드는 필수 값입니다.")
        @Schema(description = "플리마켓 초대 코드", example = "A7F233913E")
        String inviteCode
) {
}