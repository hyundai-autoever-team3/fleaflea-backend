package com.anabada.fleaflea.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 응답")
public record LoginResponse(
        @Schema(
                description = "API 요청에 사용하는 Access Token",
                example = "eyJhbGciOiJIUzI1NiJ9..."
        )
        String accessToken,

        @Schema(
                description = "Access Token 재발급에 사용하는 Refresh Token",
                example = "eyJhbGciOiJIUzI1NiJ9..."
        )
        String refreshToken
) {
}
