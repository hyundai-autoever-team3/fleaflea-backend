package com.anabada.fleaflea.domain.marketmember.dto;

import jakarta.validation.constraints.NotBlank;

public record MarketJoinRequest(

        @NotBlank(message = "초대 코드는 필수 값입니다.")
        String inviteCode
) {
}