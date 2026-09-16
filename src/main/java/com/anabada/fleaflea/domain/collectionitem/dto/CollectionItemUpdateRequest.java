package com.anabada.fleaflea.domain.collectionitem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public record CollectionItemUpdateRequest(

        @Size(
                min = 1,
                max = 150,
                message = "도감 아이템 이름은 1자 이상 150자 이하로 입력해주세요."
        )
        @Schema(description = "변경할 도감 아이템 이름")
        String title,

        @Size(
                max = 1000,
                message = "도감 아이템 설명은 1000자 이하로 입력해주세요."
        )
        @Schema(description = "변경할 도감 아이템 설명")
        String description,

        @Schema(description = "변경할 공개 여부")
        Boolean isPublic,

        @Schema(
                description = "변경할 도감 아이템 이미지",
                type = "string",
                format = "binary"
        )
        MultipartFile image
) {
}