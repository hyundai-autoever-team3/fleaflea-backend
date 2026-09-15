package com.anabada.fleaflea.domain.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public record MarketUpdateRequest(

        @Size(min = 1, max = 100,
                message = "플리마켓 이름은 1자 이상 100자 이하로 입력해주세요.")
        @Schema(description = "변경할 플리마켓 이름")
        String title,

        @Size(max = 1000,
                message = "플리마켓 설명은 1000자 이하로 입력해주세요.")
        @Schema(description = "변경할 플리마켓 설명")
        String description,

        @Schema(
                description = "변경할 커버 이미지",
                type = "string",
                format = "binary"
        )
        MultipartFile coverImage
) {
}