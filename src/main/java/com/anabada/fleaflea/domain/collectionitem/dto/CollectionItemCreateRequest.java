package com.anabada.fleaflea.domain.collectionitem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public record CollectionItemCreateRequest(

        @NotBlank(message = "도감 아이템 이름은 필수입니다.")
        @Size(max = 150, message = "도감 아이템 이름은 150자 이하로 입력해주세요.")
        @Schema(description = "도감 아이템 이름", example = "한정판 텀블러")
        String title,

        @Size(max = 1000, message = "도감 아이템 설명은 1000자 이하로 입력해주세요.")
        @Schema(description = "도감 아이템 설명")
        String description,

        @NotNull(message = "공개 여부는 필수입니다.")
        @Schema(description = "공개 여부", example = "true")
        Boolean isPublic,

        @Schema(
                description = "도감 아이템 이미지",
                type = "string",
                format = "binary"
        )
        @NotNull
        MultipartFile image
) {
}