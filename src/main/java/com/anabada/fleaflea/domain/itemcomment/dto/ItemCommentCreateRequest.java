package com.anabada.fleaflea.domain.itemcomment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ItemCommentCreateRequest(
        @NotBlank @Size(max = 2000) String content,
        @Positive @Schema(description = "대댓글 작성 시 부모 댓글 ID. 일반 댓글은 생략") Long parentId
) {
}
