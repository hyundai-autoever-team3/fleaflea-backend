package com.anabada.fleaflea.domain.item.dto;

import com.anabada.fleaflea.domain.item.domain.ItemTradeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public record ItemUpdateRequest(
        @Size(max = 150, message = "상품명은 150자 이하여야 합니다.")
        @Schema(description = "수정할 상품명", example = "애플 매직 키보드")
        String title,

        @Size(max = 2000, message = "상품 설명은 2000자 이하여야 합니다.")
        @Schema(description = "수정할 상품 설명", example = "사용감 조금 있습니다.")
        String description,

        @Schema(description = "수정할 거래 방식", example = "SALE",
                allowableValues = {"SALE", "GIVEAWAY", "RENTAL", "SWAP"})
        ItemTradeType tradeType,

        @PositiveOrZero(message = "상품 가격은 0 이상이어야 합니다.")
        @Schema(description = "수정할 상품 가격. 무료 나눔 또는 교환은 null 가능", example = "50000")
        Long price,

        @Schema(
                description = "상품 이미지 파일",
                type = "string",
                format = "binary"
        )
        MultipartFile image
) {}
