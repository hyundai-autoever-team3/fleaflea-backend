package com.anabada.fleaflea.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SearchMemberResponse(

        @Schema(description = "회원 ID", example = "1")
        Long memberId,

        @Schema(description = "회원 닉네임", example = "홍길동")
        String nickname
) {
    public static SearchMemberResponse from(
            Long memberId,
            String nickname
    ) {
        return new SearchMemberResponse(
                memberId,
                nickname
        );
    }
}
