package com.anabada.fleaflea.domain.member.dto;


import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "카카오·구글 소셜 회원가입 완료 응답")
public record OAuth2SignupResponse(
            @Schema(description = "우리 서비스 API 인증용 Access Token. Refresh Token은 HttpOnly 쿠키로 발급됩니다.")
            String accessToken
    ) {
    }
