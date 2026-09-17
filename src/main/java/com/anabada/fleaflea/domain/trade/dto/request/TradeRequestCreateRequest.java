package com.anabada.fleaflea.domain.trade.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TradeRequestCreateRequest(
        @Size(max = 1000, message = "거래 요청 메시지는 1000자 이하여야 합니다.")
        @Schema(
                description = "거래 상대방에게 남길 메시지",
                example = "이번 주 모임에서 받을 수 있을까요?"
        )
        String message,

        @FutureOrPresent(message = "대여 시작일은 오늘 이후여야 합니다.")
        @Schema(description = "대여 시작일. 대여 상품인 경우 필수", example = "2026-09-20")
        LocalDate rentalStartDate,

        @FutureOrPresent(message = "대여 종료일은 오늘 이후여야 합니다.")
        @Schema(description = "대여 종료일. 대여 상품인 경우 필수", example = "2026-09-27")
        LocalDate rentalEndDate
) {}
