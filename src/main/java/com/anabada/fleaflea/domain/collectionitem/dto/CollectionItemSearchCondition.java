package com.anabada.fleaflea.domain.collectionitem.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record CollectionItemSearchCondition(
        @Schema(description = "도감 아이템 제목 검색어", example = "텀블러")
        String title
) {
}
