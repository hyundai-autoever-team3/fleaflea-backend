package com.anabada.fleaflea.domain.friendship.event;

public record FriendRequestedEvent(
        Long friendshipId,
        Long requesterId,
        Long addresseeId,
        String requesterNickname
) {

    public static FriendRequestedEvent of(
            Long friendshipId,
            Long requesterId,
            Long addresseeId,
            String requesterNickname
    ) {
        return new FriendRequestedEvent(
                friendshipId,
                requesterId,
                addresseeId,
                requesterNickname
        );
    }
}