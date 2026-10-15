package com.anabada.fleaflea.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
public record EmailCodeVerifyResponse(
        @Schema(description = "코드 검증 결과", example = "SUCCESS", allowableValues = {"SUCCESS", "NOT_FOUND", "MISMATCH", "ATTEMPTS_EXCEED","RATE_LIMIT_EXCEEDED"})
        String result,
        @Schema(description = "SUCCESS일 때만 발급되는 일회용 인증 토큰. 유효기간 10분. 실패 시 null", nullable = true, example = "550e8400-e29b-41d4-a716-446655440000")
        String verificationToken
) {
}