package com.anabada.fleaflea.domain.begrequest.dto;

import com.anabada.fleaflea.domain.begrequest.domain.BegRequest;
import com.anabada.fleaflea.domain.begrequest.domain.BegRequestStatus;
import com.anabada.fleaflea.domain.collection.domain.CollectionItem;
import com.anabada.fleaflea.domain.member.domain.Member;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record BeggingDetailResponse(

        @Schema(description = "구걸 요청 ID", example = "1")
        Long begRequestId,

        @Schema(description = "도감 아이템 ID", example = "10")
        Long collectionItemId,

        @Schema(description = "도감 아이템 제목", example = "닌텐도 스위치")
        String collectionItemTitle,

        @Schema(description = "도감 아이템 설명", example = "닌텐도 스위치 OLED 모델입니다.")
        String collectionItemDescription,

        @Schema(description = "도감 아이템 소유자 ID", example = "2")
        Long ownerId,

        @Schema(description = "도감 아이템 소유자 닉네임", example = "영희")
        String ownerNickname,

        @Schema(description = "구걸 요청자 ID", example = "3")
        Long applicantId,

        @Schema(description = "구걸 요청자 닉네임", example = "철수")
        String applicantNickname,

        @Schema(description = "구걸 요청 사유", example = "정말 갖고 싶었던 아이템이에요!")
        String story,

        @Schema(description = "구걸 요청 상태", example = "PENDING")
        BegRequestStatus status,

        @Schema(description = "등록 시각")
        LocalDateTime createdAt,

        @Schema(description = "수정 시각")
        LocalDateTime updatedAt

) {

    public static BeggingDetailResponse from(BegRequest begRequest) {

        CollectionItem collectionItem = begRequest.getCollectionItem();
        Member owner = begRequest.getOwner();
        Member applicant = begRequest.getApplicant();

        return new BeggingDetailResponse(
                begRequest.getBegRequestId(),
                collectionItem.getCollectionItemId(),
                collectionItem.getTitle(),
                collectionItem.getDescription(),
                owner.getMemberId(),
                owner.getNickname(),
                applicant.getMemberId(),
                applicant.getNickname(),
                begRequest.getStory(),
                begRequest.getStatus(),
                begRequest.getCreatedAt(),
                begRequest.getUpdatedAt()
        );
    }
}