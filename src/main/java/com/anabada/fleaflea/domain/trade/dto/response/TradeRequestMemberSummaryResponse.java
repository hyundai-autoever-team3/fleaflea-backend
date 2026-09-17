package com.anabada.fleaflea.domain.trade.dto.response;

import com.anabada.fleaflea.domain.member.domain.Member;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "거래 목록 상대 회원 요약 응답")
public record TradeRequestMemberSummaryResponse(
        @Schema(description = "회원 ID", example = "2")
        Long memberId,

        @Schema(description = "회원 닉네임", example = "민지")
        String nickname
) {

    public static TradeRequestMemberSummaryResponse from(Member member) {
        return new TradeRequestMemberSummaryResponse(
                member.getMemberId(),
                member.getNickname()
        );
    }
}
