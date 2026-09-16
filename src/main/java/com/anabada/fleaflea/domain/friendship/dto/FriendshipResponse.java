package com.anabada.fleaflea.domain.friendship.dto;

import com.anabada.fleaflea.domain.friendship.domain.Friendship;
import com.anabada.fleaflea.domain.friendship.domain.RelationshipStatus;
import com.anabada.fleaflea.domain.member.domain.Member;

public record FriendshipResponse(
        Long friendshipId,
        Long memberId,
        String nickname,
        String profileImageUrl,
        RelationshipStatus relationshipStatus
) {
    public static FriendshipResponse from(
            Friendship friendship,
            Member member,
            String profileImageUrl,
            RelationshipStatus relationshipStatus
    ) {
        return new FriendshipResponse(
                friendship.getFriendshipId(),
                member.getMemberId(),
                member.getNickname(),
                profileImageUrl,
                relationshipStatus
        );
    }
}
