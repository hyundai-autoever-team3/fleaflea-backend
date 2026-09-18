package com.anabada.fleaflea.domain.poke.event;

public record MemberPokedEvent(
        Long pokeId,
        Long senderId,
        Long recipientId,
        String senderNickname
) {

    public static MemberPokedEvent of(
            Long pokeId,
            Long senderId,
            Long recipientId,
            String senderNickname
    ) {
        return new MemberPokedEvent(
                pokeId,
                senderId,
                recipientId,
                senderNickname
        );
    }
}