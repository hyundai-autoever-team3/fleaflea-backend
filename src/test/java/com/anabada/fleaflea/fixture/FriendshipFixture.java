package com.anabada.fleaflea.fixture;

import com.anabada.fleaflea.domain.friendship.domain.Friendship;
import com.anabada.fleaflea.domain.friendship.domain.FriendshipStatus;
import com.anabada.fleaflea.domain.member.domain.Member;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FriendshipFixture {

    public static Friendship createFriendship(
            Member requester,
            Member addressee,
            FriendshipStatus status
    ) {
        return Friendship.create(requester, addressee, status);
    }
}
