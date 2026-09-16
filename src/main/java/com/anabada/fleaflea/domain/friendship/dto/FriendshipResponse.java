package com.anabada.fleaflea.domain.friendship.dto;

import com.anabada.fleaflea.domain.friendship.domain.Friendship;
import com.anabada.fleaflea.domain.member.domain.Member;

public record FriendshipResponse(
        Long friendshipId,
        Long memberId,
        String nickname,
        String profileImageKey
) {
    public static FriendshipResponse from(
            Friendship friendship,
            Member member) {
        return new FriendshipResponse(
                friendship.getFriendshipId(),
                member.getMemberId(),
                member.getNickname(),
                member.getProfileImageKey()
        );
    }
}
