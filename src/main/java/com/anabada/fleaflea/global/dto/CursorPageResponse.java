package com.anabada.fleaflea.global.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "커서 기반 페이지 응답")
public record CursorPageResponse<T>(
        @Schema(description = "현재 페이지의 항목")
        List<T> content,

        @Schema(description = "다음 페이지 조회에 사용할 커서. 항목이 없으면 null", example = "42")
        Long nextCursor,

        @Schema(description = "다음 페이지 존재 여부", example = "true")
        boolean hasNext
) {

    public static <T> CursorPageResponse<T> from(
            List<T> content,
            Long nextCursor,
            boolean hasNext
    ) {
        return new CursorPageResponse<>(content, nextCursor, hasNext);
    }
}
