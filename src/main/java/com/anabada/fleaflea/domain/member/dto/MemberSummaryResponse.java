package com.anabada.fleaflea.domain.member.dto;

import com.anabada.fleaflea.domain.member.domain.Member;
import io.swagger.v3.oas.annotations.media.Schema;

public record MemberSummaryResponse(

        @Schema(description = "회원 ID")
        Long memberId,

        @Schema(description = "닉네임")
        String nickname,

        @Schema(description = "프로필 이미지 URL")
        String profileImageUrl
) {

    public static MemberSummaryResponse from(
            Member member,
            String profileImageUrl
    ) {
        return new MemberSummaryResponse(
                member.getMemberId(),
                member.getNickname(),
                profileImageUrl
        );
    }
}