package com.anabada.fleaflea.domain.item.dto;

import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.item.domain.ItemStatus;
import com.anabada.fleaflea.domain.item.domain.ItemTradeType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;


@Builder
@Schema(description = "상품 상세 응답")
public record ItemDetailResponse(
        @Schema(description = "상품 ID", example = "1")
        Long itemId,

        @Schema(description = "상품이 등록된 플리마켓 ID", example = "10")
        Long marketId,

        @Schema(description = "판매자 정보")
        SellerResponse seller,

        @Schema(description = "상품명", example = "애플 매직 키보드")
        String title,

        @Schema(description = "상품 설명", example = "사용감이 조금 있습니다.")
        String description,

        @Schema(description = "거래 방식", example = "SALE")
        ItemTradeType tradeType,

        @Schema(description = "상품 가격. 무료 나눔은 null 가능", example = "50000")
        Long price,

        @Schema(description = "상품 상태", example = "AVAILABLE")
        ItemStatus status,

        @Schema(description = "상품 이미지 저장 키. 이미지가 없으면 null",
                example = "items/550e8400-e29b-41d4-a716-446655440000.png")
        String imageKey,

        @Schema(description = "상품 등록 일시", example = "2026-09-15T16:00:00")
        LocalDateTime createdAt
) {

    public static ItemDetailResponse of(Item item, SellerResponse seller) {
        return ItemDetailResponse.builder()
                .itemId(item.getItemId())
                .marketId(item.getMarket().getMarketId())
                .seller(seller)
                .title(item.getTitle())
                .description(item.getDescription())
                .tradeType(item.getTradeType())
                .price(item.getPrice())
                .status(item.getStatus())
                .imageKey(item.getImageKey())
                .createdAt(item.getCreatedAt())
                .build();
    }
}
