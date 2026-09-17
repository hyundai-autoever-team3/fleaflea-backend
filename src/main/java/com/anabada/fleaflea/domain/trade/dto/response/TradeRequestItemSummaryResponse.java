package com.anabada.fleaflea.domain.trade.dto.response;

import com.anabada.fleaflea.domain.item.domain.Item;
import com.anabada.fleaflea.domain.item.domain.ItemTradeType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "거래 목록 상품 요약 응답")
public record TradeRequestItemSummaryResponse(
        @Schema(description = "상품 ID", example = "10")
        Long itemId,

        @Schema(description = "상품명", example = "체크 셔츠")
        String title,

        @Schema(description = "거래 방식", example = "GIVEAWAY")
        ItemTradeType tradeType,

        @Schema(
                description = "상품 이미지 URL. 이미지가 없으면 null",
                example = "https://example.com/items/check-shirt.png"
        )
        String imageUrl
) {

    public static TradeRequestItemSummaryResponse of(
            Item item,
            String imageUrl
    ) {
        return new TradeRequestItemSummaryResponse(
                item.getItemId(),
                item.getTitle(),
                item.getTradeType(),
                imageUrl
        );
    }
}
