package com.anabada.fleaflea.domain.member.dto;

import com.anabada.fleaflea.domain.member.domain.Member;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원 요약 정보")
public record MemberSummaryResponse(
        Long memberId,
        String nickname,
        String profileImageUrl
) {
    public static MemberSummaryResponse from(Member member, String profileImageUrl) {
        return new MemberSummaryResponse(member.getMemberId(), member.getNickname(), profileImageUrl);
    }
}
