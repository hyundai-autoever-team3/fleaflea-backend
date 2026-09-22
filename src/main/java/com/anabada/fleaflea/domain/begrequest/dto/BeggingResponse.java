package com.anabada.fleaflea.domain.begrequest.dto;

import com.anabada.fleaflea.domain.begrequest.domain.BegRequest;
import com.anabada.fleaflea.domain.begrequest.domain.BegRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record BeggingResponse(

        @Schema(description = "구걸 요청 ID")
        Long begRequestId,

        @Schema(description = "구걸 요청 아이템")
        Long collectionItemId,

        @Schema(description = "구걸 요청자")
        Long applicantId,

        @Schema(description = "구걸 요청 사유")
        String story,

        @Schema(description = "구걸 요청 상태")
        BegRequestStatus status,

        @Schema(description = "등록 시각")
        LocalDateTime createdAt,

        @Schema(description = "수정 시각")
        LocalDateTime updatedAt
) {
    public static BeggingResponse from(
            BegRequest begRequest
    ) {
        return new BeggingResponse(
                begRequest.getBegRequestId(),
                begRequest.getCollectionItemSnapshotId(),
                begRequest.getApplicant().getMemberId(),
                begRequest.getStory(),
                begRequest.getStatus(),
                begRequest.getCreatedAt(),
                begRequest.getUpdatedAt()
        );

    }
}
