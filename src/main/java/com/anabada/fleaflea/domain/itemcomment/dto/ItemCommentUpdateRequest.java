package com.anabada.fleaflea.domain.itemcomment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ItemCommentUpdateRequest(
        @NotBlank @Size(max = 2000) String content
) {
}
