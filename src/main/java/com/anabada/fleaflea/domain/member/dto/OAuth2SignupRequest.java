package com.anabada.fleaflea.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "카카오 회원가입 완료 요청")
public record OAuth2SignupRequest(
        @Schema(
                description = "우리 서비스에서 사용할 닉네임",
                example = "홍길동",
                maxLength = 50
        )
        @NotBlank
        @Size(max = 50)
        String nickname
) {
}
