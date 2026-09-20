package com.anabada.fleaflea.domain.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record MarketSearchCondition(
        @Schema(description = "플리마켓 제목 검색어", example = "동네 장터")
        String title
) {
}
