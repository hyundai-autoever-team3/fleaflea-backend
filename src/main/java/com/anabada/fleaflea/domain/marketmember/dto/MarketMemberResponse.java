package com.anabada.fleaflea.domain.marketmember.dto;

import com.anabada.fleaflea.domain.marketmember.domain.MarketMember;
import com.anabada.fleaflea.domain.member.domain.Member;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record MarketMemberResponse(

        @Schema(description = "회원 ID", example = "1")
        Long memberId,

        @Schema(description = "회원 닉네임", example = "홍길동")
        String nickname,

        @Schema(description = "프로필 이미지 URL")
        String profileImageUrl,

        @Schema(description = "플리마켓 개설자 여부", example = "true")
        boolean host,

        @Schema(description = "플리마켓 참여 시각")
        LocalDateTime joinedAt
) {

    public static MarketMemberResponse from(
            MarketMember marketMember,
            String profileImageUrl,
            Long hostId
    ) {
        Member member = marketMember.getMember();

        return new MarketMemberResponse(
                member.getMemberId(),
                member.getNickname(),
                profileImageUrl,
                member.getMemberId().equals(hostId),
                marketMember.getJoinedAt()
        );
    }
}