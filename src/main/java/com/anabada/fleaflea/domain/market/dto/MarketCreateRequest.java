package com.anabada.fleaflea.domain.market.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MarketCreateRequest(

        @NotBlank(message = "플리마켓 이름은 필수 값입니다.")
        @Size(max = 100, message = "플리마켓 이름은 100자 이하로 입력해주세요.")
        String title,

        @Size(max = 1000, message = "플리마켓 설명은 1000자 이하로 입력해주세요.")
        String description
) {
}