package com.anabada.fleaflea.domain.item.dto;

import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.item.domain.ItemStatus;
import com.anabada.fleaflea.domain.item.domain.ItemTradeType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;


@Builder
@Schema(description = "상품 요약 응답")
public record ItemSummaryResponse(
        @Schema(description = "상품 ID", example = "1")
        Long itemId,

        @Schema(description = "상품명", example = "애플 매직 키보드")
        String title,

        @Schema(description = "거래 방식", example = "SALE")
        ItemTradeType tradeType,

        @Schema(description = "상품 가격. 무료 나눔은 null 가능", example = "50000")
        Long price,

        @Schema(description = "상품 상태", example = "AVAILABLE")
        ItemStatus status,

        @Schema(description = "상품 이미지 URL. 이미지가 없으면 null",
                example = "https://example.com/items/item.png")
        String imageUrl,

        @Schema(description = "상품 등록 일시", example = "2026-09-15T16:00:00")
        LocalDateTime createdAt
) {

    public static ItemSummaryResponse of(Item item, String imageUrl) {
        return ItemSummaryResponse.builder()
                .itemId(item.getItemId())
                .title(item.getTitle())
                .tradeType(item.getTradeType())
                .price(item.getPrice())
                .status(item.getStatus())
                .imageUrl(imageUrl)
                .createdAt(item.getCreatedAt())
                .build();
    }

    public static ItemSummaryResponse of(ItemSummaryProjection projection, String imageUrl) {
        return ItemSummaryResponse.builder()
                .itemId(projection.itemId())
                .title(projection.title())
                .tradeType(projection.tradeType())
                .price(projection.price())
                .status(projection.status())
                .imageUrl(imageUrl)
                .createdAt(projection.createdAt())
                .build();
    }
}
