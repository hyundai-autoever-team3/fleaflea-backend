package com.anabada.fleaflea.domain.friendship.event;

public record FriendAcceptedEvent(
        Long friendshipId,
        Long requesterId,
        Long addresseeId,
        String addresseeNickname
) {

    public static FriendAcceptedEvent of(
            Long friendshipId,
            Long requesterId,
            Long addresseeId,
            String addresseeNickname
    ) {
        return new FriendAcceptedEvent(
                friendshipId,
                requesterId,
                addresseeId,
                addresseeNickname
        );
    }
}