package com.anabada.fleaflea.domain.collectionitem.dto;

import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record CollectionItemSummaryResponse(

        @Schema(description = "도감 아이템 ID")
        Long collectionItemId,

        @Schema(description = "도감 아이템 이름")
        String title,

        @Schema(description = "도감 아이템 이미지 URL")
        String imageUrl,

        @Schema(description = "공개 여부")
        Boolean isPublic,

        @Schema(description = "등록 시각")
        LocalDateTime createdAt
) {

    public static CollectionItemSummaryResponse from(
            CollectionItem collectionItem,
            String imageUrl
    ) {
        return new CollectionItemSummaryResponse(
                collectionItem.getCollectionItemId(),
                collectionItem.getTitle(),
                imageUrl,
                collectionItem.getIsPublic(),
                collectionItem.getCreatedAt()
        );
    }
}
