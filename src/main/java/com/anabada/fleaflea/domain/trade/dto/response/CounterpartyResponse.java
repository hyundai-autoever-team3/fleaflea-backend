package com.anabada.fleaflea.domain.trade.dto.response;

import com.anabada.fleaflea.domain.member.domain.Member;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "거래 상대 정보")
public record CounterpartyResponse(

        @Schema(description = "회원 ID", example = "2")
        Long memberId,

        @Schema(description = "닉네임", example = "민지")
        String nickname,

        @Schema(
                description = "프로필 이미지 URL. 이미지가 없으면 null",
                example = "https://example.com/profiles/minji.png"
        )
        String profileImageUrl
) {

    public static CounterpartyResponse of(
            Member member,
            String profileImageUrl
    ) {
        return new CounterpartyResponse(
                member.getMemberId(),
                member.getNickname(),
                profileImageUrl
        );
    }
}