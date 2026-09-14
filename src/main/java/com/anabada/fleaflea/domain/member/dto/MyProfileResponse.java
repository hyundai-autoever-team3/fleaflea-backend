package com.anabada.fleaflea.domain.member.dto;

import com.anabada.fleaflea.domain.member.domain.Member;

public record MyProfileResponse(
    Long memberId,
    String email,
    String nickname,
    String profileImageUrl
) {

    public static MyProfileResponse from(Member member) {
        return new MyProfileResponse(
                member.getMemberId(),
                member.getEmail(),
                member.getNickname(),
                member.getProfileImageUrl()
        );
    }

}
