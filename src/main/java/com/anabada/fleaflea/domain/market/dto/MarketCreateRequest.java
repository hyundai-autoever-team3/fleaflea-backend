package com.anabada.fleaflea.domain.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MarketCreateRequest(

        @NotBlank(message = "플리마켓 이름은 필수 값입니다.")
        @Size(max = 100, message = "플리마켓 이름은 100자 이하로 입력해주세요.")
        @Schema(description = "플리마켓 이름", example = "우리 동네 플리마켓")
        String title,

        @Size(max = 1000, message = "플리마켓 설명은 1000자 이하로 입력해주세요.")
        @Schema(description = "플리마켓 설명", example = "사용하지 않는 물건을 나누는 플리마켓입니다.")
        String description
) {
}