package com.anabada.fleaflea.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

@Schema(description = "로그아웃 요청. 본문과 deviceId는 생략 가능")
public record LogoutRequest(
        @Positive(message = "기기 ID는 양수여야 합니다.")
        @Schema(description = "현재 기기의 푸시 등록 ID. 지정한 본인 기기만 해제하며 생략하면 푸시 등록은 유지",
                example = "1")
        Long deviceId
) {

    public static LogoutRequest of(Long deviceId) {
        return new LogoutRequest(deviceId);
    }
}
