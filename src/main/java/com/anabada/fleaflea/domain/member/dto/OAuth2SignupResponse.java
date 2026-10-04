package com.anabada.fleaflea.domain.member.dto;


import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "카카오 회원가입 완료 응답")
public record OAuth2SignupResponse(
            String accessToken
    ) {
    }
