package com.anabada.fleaflea.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
                로그인 응답입니다.
                Refresh Token은 응답 body에 포함되지 않으며
                HttpOnly Cookie로 전달됩니다.
                """)
public record LoginResponse(
        @Schema(
                description = """
                        API 요청의 Authorization 헤더에 Bearer 형식으로 넣는 Access Token
                        """,
                example = "eyJhbGciOiJIUzI1NiJ9..."
        )
        String accessToken
) {
}
