package com.anabada.fleaflea.domain.collectionitem.dto;

import com.anabada.fleaflea.domain.member.dto.MemberSummaryResponse;
import com.anabada.fleaflea.global.dto.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;

public record MemberCollectionResponse(

        @Schema(description = "도감 소유자 정보")
        MemberSummaryResponse owner,

        @Schema(description = "공개 도감 아이템 목록과 페이지 정보")
        PageResponse<CollectionItemSummaryResponse> items
) {
}