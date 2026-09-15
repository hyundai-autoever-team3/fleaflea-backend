package com.anabada.fleaflea.domain.friendship.dto;

import com.anabada.fleaflea.domain.member.domain.Member;

public record FriendshipResponse(
        Long memberId,
        String nickname,
        String profileImageKey
) {
    public static FriendshipResponse from(Member member) {
        return new FriendshipResponse(
                member.getMemberId(),
                member.getNickname(),
                member.getProfileImageKey()
        );
    }
}
