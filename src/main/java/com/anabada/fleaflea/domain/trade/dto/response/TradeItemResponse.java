package com.anabada.fleaflea.domain.trade.dto.response;

import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.item.domain.ItemTradeType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "거래 상품 정보")
public record TradeItemResponse(

        @Schema(description = "상품 ID", example = "10")
        Long itemId,

        @Schema(description = "상품명", example = "보라 체크 셔츠")
        String title,

        @Schema(description = "거래 방식", example = "GIVEAWAY")
        ItemTradeType tradeType,

        @Schema(description = "상품 가격. 나눔이면 null", example = "2000")
        Long price,

        @Schema(
                description = "상품 이미지 URL. 이미지가 없으면 null",
                example = "https://example.com/items/check-shirt.png"
        )
        String imageUrl
) {

    public static TradeItemResponse of(
            Item item,
            String imageUrl
    ) {
        return new TradeItemResponse(
                item.getItemId(),
                item.getTitle(),
                item.getTradeType(),
                item.getPrice(),
                imageUrl
        );
    }
}