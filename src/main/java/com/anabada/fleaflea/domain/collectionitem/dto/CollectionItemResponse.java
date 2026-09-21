package com.anabada.fleaflea.domain.collectionitem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import com.anabada.fleaflea.domain.collection.domain.CollectionItemStatus;

import java.time.LocalDateTime;

public record CollectionItemResponse(

        @Schema(description = "도감 아이템 ID")
        Long collectionItemId,

        @Schema(description = "소유자 회원 ID")
        Long ownerId,

        @Schema(description = "소유자 닉네임")
        String ownerNickname,

        @Schema(description = "도감 아이템 이름")
        String title,

        @Schema(description = "도감 아이템 설명")
        String description,

        @Schema(description = "도감 아이템 이미지 URL")
        String imageUrl,

        @Schema(description = "공개 여부")
        Boolean isPublic,

        @Schema(
                description = "도감 아이템 거래 상태",
                allowableValues = {"AVAILABLE", "IN_PROGRESS"}
        )
        CollectionItemStatus status,

        @Schema(description = "등록 시각")
        LocalDateTime createdAt,

        @Schema(description = "수정 시각")
        LocalDateTime updatedAt
) {

    public static CollectionItemResponse from(
            CollectionItem collectionItem,
            String imageUrl,
            CollectionItemStatus status
    ) {
        return new CollectionItemResponse(
                collectionItem.getCollectionItemId(),
                collectionItem.getOwner().getMemberId(),
                collectionItem.getOwner().getNickname(),
                collectionItem.getTitle(),
                collectionItem.getDescription(),
                imageUrl,
                collectionItem.getIsPublic(),
                status,
                collectionItem.getCreatedAt(),
                collectionItem.getUpdatedAt()
        );
    }
}